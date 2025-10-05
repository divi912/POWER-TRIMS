package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HostTrim implements Listener {

    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    private final Map<UUID, Set<PotionEffectType>> amplifiedEffectsTracker = new ConcurrentHashMap<>();

    // --- CONSTANTS ---
    private final long ESSENCE_REAPER_COOLDOWN;
    private final double EFFECT_STEAL_RADIUS;
    private final double HEALTH_STEAL_AMOUNT;
    private final double PARTICLE_DENSITY;

    private static final int MAX_STEALABLE_DURATION = 36000; // 30 minutes in ticks

    private static final Set<EntityType> BOSS_MOBS = EnumSet.of(
            EntityType.WARDEN,
            EntityType.ENDER_DRAGON,
            EntityType.WITHER
    );

    private static final Set<PotionEffectType> POSITIVE_EFFECTS = Set.of(
            PotionEffectType.SPEED, PotionEffectType.REGENERATION, PotionEffectType.STRENGTH,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.RESISTANCE, PotionEffectType.ABSORPTION
    );

    public HostTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        ESSENCE_REAPER_COOLDOWN = configManager.getLong("host.primary.cooldown");
        EFFECT_STEAL_RADIUS = configManager.getDouble("host.primary.effect_steal_radius");
        HEALTH_STEAL_AMOUNT = configManager.getDouble("host.primary.health_steal_amount");
        PARTICLE_DENSITY = configManager.getDouble("host.primary.particle_density");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        abilityManager.registerPrimaryAbility(TrimPattern.HOST, this::activateHostPrimary);
    }

    @EventHandler
    public void onPotionEffectEnd(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED ||
                event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {

            PotionEffectType type = event.getModifiedType();
            Set<PotionEffectType> trackedEffects = amplifiedEffectsTracker.get(player.getUniqueId());

            if (trackedEffects != null && trackedEffects.remove(type)) {
                if (trackedEffects.isEmpty()) {
                    amplifiedEffectsTracker.remove(player.getUniqueId());
                }
            }
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player targetPlayer) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (BOSS_MOBS.contains(mob.getType())) {
            return;
        }

        if (ArmourChecking.hasFullTrimmedArmor(targetPlayer, TrimPattern.HOST)) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateHostPrimary(Player player) {
        if (!configManager.isTrimEnabled("host")) {
            return;
        }
        if (cooldownManager.isOnCooldown(player, TrimPattern.HOST) ||
                !ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.HOST)) {
            return;
        }

        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        Set<PotionEffectType> stolenThisActivation = new HashSet<>();

        for (Player targetPlayer : world.getNearbyPlayers(playerLoc, EFFECT_STEAL_RADIUS)) {
            if (targetPlayer.equals(player) || trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                continue;
            }

            for (PotionEffect effect : new ArrayList<>(targetPlayer.getActivePotionEffects())) {
                PotionEffectType type = effect.getType();

                // --- MODIFIED: Added 'effect.getDuration() > 0' to filter out infinite (-1) effects ---
                if (effect.getDuration() > 0 && effect.getDuration() < MAX_STEALABLE_DURATION &&
                        POSITIVE_EFFECTS.contains(type) &&
                        stolenThisActivation.add(type) &&
                        !isEffectAmplifiedByHost(targetPlayer.getUniqueId(), type)) {

                    targetPlayer.removePotionEffect(type);
                    player.addPotionEffect(new PotionEffect(type, effect.getDuration(), effect.getAmplifier() + 1, true, true));

                    amplifiedEffectsTracker.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(type);
                }
            }

            double targetHealth = targetPlayer.getHealth();
            double healthToSteal = Math.min(targetHealth, HEALTH_STEAL_AMOUNT);

            if (healthToSteal > 0) {
                targetPlayer.setHealth(Math.max(0, targetHealth - healthToSteal));
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healthToSteal));
                createParticleTrail(targetPlayer.getLocation(), playerLoc, world);
            }
        }

        world.playSound(playerLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        world.spawnParticle(Particle.SOUL, playerLoc, 30, 1, 1, 1, 0.1);

        cooldownManager.setCooldown(player, TrimPattern.HOST, ESSENCE_REAPER_COOLDOWN);
        Messaging.sendTrimMessage(player, "Host", ChatColor.DARK_PURPLE, "Essence Reaper activated!");
    }

    private boolean isEffectAmplifiedByHost(UUID playerUUID, PotionEffectType type) {
        Set<PotionEffectType> trackedEffects = amplifiedEffectsTracker.get(playerUUID);
        return trackedEffects != null && trackedEffects.contains(type);
    }

    private void createParticleTrail(Location start, Location end, World world) {
        Vector direction = end.toVector().subtract(start.toVector());

        if (direction.lengthSquared() < 0.001) {
            return;
        }

        double distance = direction.length();
        Vector step = direction.normalize().multiply(1.0 / PARTICLE_DENSITY);
        int steps = (int) (distance * PARTICLE_DENSITY);

        Location currentPoint = start.clone();
        for (int i = 0; i < steps; i++) {
            currentPoint.add(step);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, currentPoint, 1, 0, 0, 0, 0);
        }
    }
}
