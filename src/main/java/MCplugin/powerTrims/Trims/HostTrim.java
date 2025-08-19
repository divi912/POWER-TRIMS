package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.PersistentTrustManager;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class HostTrim implements Listener {

    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final int activationSlot;

    // --- CONSTANTS ---
    private static final long ESSENCE_REAPER_COOLDOWN = 120_000L; // 2 minutes
    private static final double EFFECT_STEAL_RADIUS = 10.0;
    private static final double HEALTH_STEAL_AMOUNT = 4.0; // 2 hearts
    private static final double PARTICLE_DENSITY = 4.0; // Particles per block

    // Using Sets for efficient 'contains' checks and better readability
    private static final Set<EntityType> BOSS_MOBS = EnumSet.of(
            EntityType.WARDEN,
            EntityType.ENDER_DRAGON,
            EntityType.WITHER
    );

    private static final Set<PotionEffectType> POSITIVE_EFFECTS = Set.of(
            PotionEffectType.SPEED, PotionEffectType.REGENERATION, PotionEffectType.STRENGTH,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.RESISTANCE, PotionEffectType.ABSORPTION
    );

    public HostTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.activationSlot = plugin.getConfig().getInt("activation-slot", 8);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player targetPlayer) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }

        // Ignore boss mobs
        if (BOSS_MOBS.contains(mob.getType())) {
            return;
        }

        if (ArmourChecking.hasFullTrimmedArmor(targetPlayer, TrimPattern.HOST)) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        // Use the constant and check both conditions
        if (event.getNewSlot() == activationSlot && event.getPlayer().isSneaking()) {
            activateHostPrimary(event.getPlayer());
        }
    }

    public void activateHostPrimary(Player player) {
        // Combine guard clauses for cleaner code
        if (cooldownManager.isOnCooldown(player, TrimPattern.HOST) ||
                !ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.HOST)) {
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        // Use a LOCAL set to track effects stolen in THIS activation to avoid memory leaks
        Set<PotionEffectType> stolenThisActivation = new HashSet<>();

        // Use getNearbyPlayers for better performance
        for (Player targetPlayer : world.getNearbyPlayers(playerLoc, EFFECT_STEAL_RADIUS, EFFECT_STEAL_RADIUS, EFFECT_STEAL_RADIUS)) {
            if (targetPlayer.equals(player) || trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                continue;
            }

            // Steal Potion Effects
            // Iterate over a copy to prevent ConcurrentModificationException
            for (PotionEffect effect : new ArrayList<>(targetPlayer.getActivePotionEffects())) {
                PotionEffectType type = effect.getType();

                // Check if it's a positive effect AND we haven't stolen this type yet in this activation
                if (POSITIVE_EFFECTS.contains(type) && stolenThisActivation.add(type)) {
                    targetPlayer.removePotionEffect(type);
                    player.addPotionEffect(new PotionEffect(type, effect.getDuration(), effect.getAmplifier() + 1, true, true));
                }
            }

            // Steal Health
            double targetHealth = targetPlayer.getHealth();
            double healthToSteal = Math.min(targetHealth, HEALTH_STEAL_AMOUNT);

            if (healthToSteal > 0) {
                targetPlayer.setHealth(Math.max(0, targetHealth - healthToSteal));
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healthToSteal));
                createParticleTrail(playerLoc, targetPlayer.getLocation(), world);
            }
        }

        world.playSound(playerLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        world.spawnParticle(Particle.SOUL, playerLoc, 30, 1, 1, 1, 0.1);

        cooldownManager.setCooldown(player, TrimPattern.HOST, ESSENCE_REAPER_COOLDOWN);
        player.sendMessage(ChatColor.DARK_PURPLE + "Essence Reaper activated!");
    }

    private void createParticleTrail(Location start, Location end, World world) {
        Vector direction = end.toVector().subtract(start.toVector());

        // Avoid division by zero if start and end are the same
        if (direction.lengthSquared() < 0.001) {
            return;
        }

        double distance = direction.length();
        Vector step = direction.normalize().multiply(1.0 / PARTICLE_DENSITY);
        int steps = (int) (distance * PARTICLE_DENSITY);

        // Use a mutable location to avoid creating new Vector objects in the loop
        Location currentPoint = start.clone();
        for (int i = 0; i < steps; i++) {
            currentPoint.add(step);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, currentPoint, 1, 0, 0, 0, 0);
        }
    }
}