package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class HostTrim implements Listener {

    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final Random random = new Random();

    private final Map<UUID, Set<PotionEffectType>> amplifiedEffectsTracker = new ConcurrentHashMap<>();

    private final long ESSENCE_REAPER_COOLDOWN;
    private final double EFFECT_STEAL_RADIUS;
    private final double HEALTH_STEAL_AMOUNT;
    private static final List<Material> SOUL_MATERIALS = List.of(
            Material.SOUL_SOIL, Material.SOUL_SAND, Material.SCULK, Material.AMETHYST_BLOCK
    );

    private static final int MAX_STEALABLE_DURATION = 36000;

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
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        ESSENCE_REAPER_COOLDOWN = configManager.getLong("host.primary.cooldown");
        EFFECT_STEAL_RADIUS = configManager.getDouble("host.primary.effect_steal_radius");
        HEALTH_STEAL_AMOUNT = configManager.getDouble("host.primary.health_steal_amount");

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
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateHostPrimary(Player player) {
        if (!configManager.isTrimEnabled("host")) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.HOST) || !ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.HOST)) return;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        Set<PotionEffectType> stolenThisActivation = new HashSet<>();

        world.playSound(playerLoc, Sound.ENTITY_VEX_CHARGE, 1.2f, 0.8f);
        world.playSound(playerLoc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.8f, 2.0f);
        playActivationVortex(player);

        for (Player targetPlayer : world.getNearbyPlayers(playerLoc, EFFECT_STEAL_RADIUS)) {
            if (targetPlayer.equals(player) || trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) continue;

            for (PotionEffect effect : new ArrayList<>(targetPlayer.getActivePotionEffects())) {
                PotionEffectType type = effect.getType();
                if (POSITIVE_EFFECTS.contains(type) && effect.getDuration() < MAX_STEALABLE_DURATION && stolenThisActivation.add(type)) {
                    targetPlayer.removePotionEffect(type);
                    player.addPotionEffect(new PotionEffect(type, effect.getDuration(), effect.getAmplifier() + 1, true, true));
                    amplifiedEffectsTracker.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(type);

                    playSiphonAnimation(targetPlayer.getEyeLocation(), player.getEyeLocation(), 15, Material.AMETHYST_BLOCK);
                }
            }

            double healthToSteal = Math.min(targetPlayer.getHealth() - 1, HEALTH_STEAL_AMOUNT);
            if (healthToSteal > 0) {
                targetPlayer.setHealth(targetPlayer.getHealth() - healthToSteal);
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healthToSteal));

                playSiphonAnimation(targetPlayer.getEyeLocation(), player.getEyeLocation(), 10, Material.REDSTONE_BLOCK);
            }
        }

        cooldownManager.setCooldown(player, TrimPattern.HOST, ESSENCE_REAPER_COOLDOWN);
        Messaging.sendTrimMessage(player, "Host", ChatColor.DARK_PURPLE, "Essence Reaper activated!");
    }

    private void playActivationVortex(Player player) {
        Location center = player.getLocation();
        int particleCount = 40;
        double radius = 3.0;

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double angle = r.nextDouble(Math.PI * 2);
            Location startLoc = center.clone().add(Math.cos(angle) * radius, r.nextDouble(2.5), Math.sin(angle) * radius);
            Material material = SOUL_MATERIALS.get(r.nextInt(SOUL_MATERIALS.size()));

            BlockDisplay particle = center.getWorld().spawn(startLoc, BlockDisplay.class, bd -> {
                bd.setBlock(material.createBlockData());
                bd.setInterpolationDuration(25);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(r.nextFloat() * 0.4f + 0.2f);
                bd.setTransformation(t);
            });

            Location endLoc = player.getEyeLocation().add(r.nextGaussian() * 0.2, r.nextGaussian() * 0.2, r.nextGaussian() * 0.2);
            Transformation endTransform = particle.getTransformation();
            endTransform.getScale().set(0f);
            endTransform.getLeftRotation().rotateY((float) (Math.PI * 3));

            particle.teleport(endLoc);
            particle.setTransformation(endTransform);

            new BukkitRunnable() {
                @Override
                public void run() { particle.remove(); }
            }.runTaskLater(plugin, 26L);
        }
    }

    private void playSiphonAnimation(Location start, Location end, int particleCount, Material material) {
        World world = start.getWorld();
        if (world == null) return;

        int travelTicks = 20;

        for (int i = 0; i < particleCount; i++) {
            BlockDisplay particle = world.spawn(start, BlockDisplay.class, bd -> {
                bd.setBlock(material.createBlockData());
                bd.setInterpolationDuration(travelTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(ThreadLocalRandom.current().nextFloat() * 0.2f + 0.1f);
                bd.setTransformation(t);
            });

            Location finalLocation = end.clone().add(
                    ThreadLocalRandom.current().nextGaussian() * 0.5,
                    ThreadLocalRandom.current().nextGaussian() * 0.5,
                    ThreadLocalRandom.current().nextGaussian() * 0.5
            );

            particle.teleport(finalLocation);
            Transformation finalTransform = particle.getTransformation();
            finalTransform.getScale().set(0f);
            particle.setTransformation(finalTransform);

            new BukkitRunnable() {
                @Override
                public void run() { particle.remove(); }
            }.runTaskLater(plugin, travelTicks + 1);
        }
    }

    private boolean isEffectAmplifiedByHost(UUID playerUUID, PotionEffectType type) {
        Set<PotionEffectType> trackedEffects = amplifiedEffectsTracker.get(playerUUID);
        return trackedEffects != null && trackedEffects.contains(type);
    }
}
