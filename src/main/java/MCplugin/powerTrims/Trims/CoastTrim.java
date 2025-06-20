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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class CoastTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; 
    private final NamespacedKey effectKey;

    // Constants for ability behavior
    private static final int WATER_BURST_RADIUS = 30; // Radius to affect nearby entities
    private static final int WATER_BURST_DAMAGE = 10; // Damage dealt to affected entities
    private static final long WATER_BURST_COOLDOWN = 60000; // Cooldown in milliseconds

    public CoastTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.effectKey = new NamespacedKey(plugin, "coast_trim_effect");
    }


    // Activates the Coast Trim ability: Water Burst
    public void CoastPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.COAST)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.COAST)) return;

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        Player coastUser = player; // Store the player using the ability

        // Play activation sound effects
        world.playSound(playerLoc, Sound.ENTITY_DOLPHIN_PLAY, 1.0f, 1.5f);
        world.playSound(playerLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);

        // Create a water particle ring around the player
        for (double angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            double offsetX = Math.cos(rad) * 1.5;
            double offsetZ = Math.sin(rad) * 1.5;
            Location effectLoc = playerLoc.clone().add(offsetX, 0.5, offsetZ);
            world.spawnParticle(Particle.FALLING_WATER, effectLoc, 20, 0.1, 0.1, 0.1, 0.1);
        }

        // Create a cloud effect above the player
        world.spawnParticle(Particle.CLOUD, playerLoc.clone().add(0, 1, 0), 50, 1, 0.5, 1, 0.1);

        // Affect nearby entities: pull them, damage them, and apply debuffs
        for (Entity entity : world.getNearbyEntities(playerLoc, WATER_BURST_RADIUS, WATER_BURST_RADIUS, WATER_BURST_RADIUS)) {
            if (entity instanceof LivingEntity target && !target.equals(coastUser)) {
                if (target instanceof Player targetPlayer && trustManager.isTrusted(coastUser.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue; // Skip trusted players
                }
                // Apply instant damage and potion debuffs
                target.damage(WATER_BURST_DAMAGE);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1)); // 4 seconds
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1)); // 4 seconds
                world.spawnParticle(Particle.SPLASH, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

                // Continuously pull the entity toward the player
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!target.isValid() || target.getLocation().distance(coastUser.getLocation()) < 2) {
                            cancel();
                            return;
                        }
                        Vector pullDirection = coastUser.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.0);
                        target.setVelocity(pullDirection);
                    }
                }.runTaskTimer(plugin, 0L, 1L); // Pull every tick
            }
        }

        // Apply buffs to the player
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));      // Speed II for 5 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0)); // Resistance I for 5 seconds

        // Set ability cooldown
        cooldownManager.setCooldown(player, TrimPattern.COAST, WATER_BURST_COOLDOWN);

        // Send activation message to the player
        Component message = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("Coast", NamedTextColor.DARK_AQUA))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text("You have activated ", NamedTextColor.GRAY))
                .append(Component.text("Water Burst", NamedTextColor.AQUA))
                .append(Component.text("!", NamedTextColor.GRAY));
        player.sendMessage(message);
    }

    // Listens for the player hotbar switch + sneak combo to activate Water Burst
    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            CoastPrimary(player);
        }
    }
}