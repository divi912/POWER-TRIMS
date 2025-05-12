/*
 * This file is part of [ POWER TRIMS ].
 *
 * [POWER TRIMS] is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * [ POWER TRIMS ] is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with [Your Plugin Name].  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) [2025] [ div ].
 */



package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.PersistentTrustManager; 
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
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
    private final NamespacedKey effectKey;
    private final Set<UUID> activeGuards;

    private static final int ARROW_COUNT = 4;
    private static final double SPREAD = 0.15;
    private static final int COOLDOWN = 90 * 1000; // 90 seconds

    public SentryTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.effectKey = new NamespacedKey(plugin, "sentry_trim_effect");
        this.activeGuards = new HashSet<>();
        SentryPassive();
    }

    private void SentryPassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SENTRY)) {
                    if (!player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, true, false, true));
                        player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                    }
                } else {
                    if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                        player.removePotionEffect(PotionEffectType.RESISTANCE);
                        player.getPersistentDataContainer().remove(effectKey);
                    }
                }
            }
        }, 0L, 20L);
    }

    public void SentryPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SENTRY) ||
                cooldownManager.isOnCooldown(player, TrimPattern.SENTRY)) return;

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

        player.sendMessage(ChatColor.GRAY + "[" + ChatColor.YELLOW + "Sentry" + ChatColor.GRAY + "] "
                + ChatColor.GOLD + "Barrage launched!");
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
        double trueDamage = 1.0; // Adjust as needed
        double newHealth = Math.max(0, target.getHealth() - trueDamage);
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
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            SentryPrimary(player);
        }
    }
}