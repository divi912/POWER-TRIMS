package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SentryTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final NamespacedKey effectKey;
    private final Set<UUID> activeGuards;

    // --- CONSTANTS ---
    private final int ARROW_COUNT;
    private final double SPREAD;
    private final long COOLDOWN;
    private final double TRUE_DAMAGE;

    public SentryTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.configManager = configManager;
        this.abilityManager = abilityManager;
        this.effectKey = new NamespacedKey(plugin, "sentry_trim_effect");
        this.activeGuards = new HashSet<>();

        ARROW_COUNT = configManager.getInt("sentry.primary.arrow_count");
        SPREAD = configManager.getDouble("sentry.primary.spread");
        COOLDOWN = configManager.getLong("sentry.primary.cooldown");
        TRUE_DAMAGE = configManager.getDouble("sentry.primary.true_damage");

        abilityManager.registerPrimaryAbility(TrimPattern.SENTRY, this::SentryPrimary);
    }


    public void SentryPrimary(Player player) {
        if (!configManager.isTrimEnabled("sentry")) {
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SENTRY) ||
                cooldownManager.isOnCooldown(player, TrimPattern.SENTRY)) return;

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        Location eyeLoc = player.getEyeLocation();
        World world = player.getWorld();
        Player sentryUser = player; // Store the player using the ability

        // Find the nearest LivingEntity (excluding the shooter and trusted players) within 15 blocks
        double radius = 15;
        LivingEntity nearestTarget = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(eyeLoc, radius, radius, radius)) {
            if (entity instanceof LivingEntity && !entity.equals(sentryUser)) {
                if (entity instanceof Player targetPlayer && trustManager.isTrusted(sentryUser.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue; // Skip trusted players
                }
                double distance = entity.getLocation().distance(eyeLoc);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestTarget = (LivingEntity) entity;
                }
            }
        }

        // Determine the base direction for the arrows
        Vector baseDirection;
        if (nearestTarget != null) {
            // Aim at the target's center (add half its height to get a better aim)
            Location targetLoc = nearestTarget.getLocation().clone().add(0, nearestTarget.getHeight() / 2, 0);
            baseDirection = targetLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        } else {
            baseDirection = eyeLoc.getDirection().clone().normalize();
        }

        // Fire spectral arrows in a slightly randomized cone around the base direction
        for (int i = 0; i < ARROW_COUNT; i++) {
            Vector direction = baseDirection.clone();
            direction.add(new Vector(
                    (Math.random() - 0.5) * SPREAD,
                    (Math.random() - 0.5) * SPREAD,
                    (Math.random() - 0.5) * SPREAD
            ));
            direction.normalize().multiply(3);

            SpectralArrow arrow = player.launchProjectile(SpectralArrow.class, direction);
            arrow.setKnockbackStrength(1);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setGlowing(true);

            // Store the shooter's UUID for custom damage handling
            arrow.getPersistentDataContainer().set(new NamespacedKey(plugin, "true_damage_arrow"), PersistentDataType.STRING, player.getUniqueId().toString());

            world.spawnParticle(Particle.CRIT, arrow.getLocation(), 5, 0.1, 0.1, 0.1, 0.05);
            world.playSound(arrow.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.2f);
        }


        // Create the barrage particle effect at the player's eye level
        createBarrageEffect(player);

        Messaging.sendTrimMessage(player, "Sentry", ChatColor.YELLOW, "Barrage launched!");
        world.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);

        cooldownManager.setCooldown(player, TrimPattern.SENTRY, COOLDOWN);
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof SpectralArrow arrow)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;

        // Check if the arrow has the true damage key
        String shooterUUID = arrow.getPersistentDataContainer().get(new NamespacedKey(plugin, "true_damage_arrow"), PersistentDataType.STRING);
        if (shooterUUID == null) return;
        Player shooter = Bukkit.getPlayer(UUID.fromString(shooterUUID));
        if (shooter == null) return;

        // Don't apply true damage to trusted players
        if (target instanceof Player targetPlayer && trustManager.isTrusted(shooter.getUniqueId(), targetPlayer.getUniqueId())) {
            return;
        }

        // Cancel vanilla damage
        event.setCancelled(true);

        // Apply true damage (bypasses armor, enchantments, and resistance)
        double newHealth = Math.max(0, target.getHealth() - TRUE_DAMAGE);
        target.setHealth(newHealth);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 1, false, true, true));

        // Additional hit effects
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
    }


    private void createBarrageEffect(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Huge explosion burst (subtle effect)
        world.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0.1);

        // Create a swirling ring of witch spell particles around the player
        int particleCount = 30;
        double radius = 2.0;
        for (int i = 0; i < particleCount; i++) {
            double angle = (2 * Math.PI / particleCount) * i;
            double offsetX = radius * Math.cos(angle);
            double offsetZ = radius * Math.sin(angle);
            Location ringLoc = loc.clone().add(offsetX, 0, offsetZ);
            world.spawnParticle(Particle.WITCH, ringLoc, 5, 0.2, 0.2, 0.2, 0.05);
        }

        // Spawn a burst of crit magic particles for added glow
        world.spawnParticle(Particle.CRIT, loc, 20, 0.5, 0.5, 0.5, 0.1);
    }


    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (!configManager.isTrimEnabled("sentry")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }
}
