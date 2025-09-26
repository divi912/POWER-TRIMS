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
import MCplugin.powerTrims.Logic.ConfigManager;
import MCplugin.powerTrims.Logic.PersistentTrustManager;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class DuneTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final ConfigManager configManager;
    private final NamespacedKey effectKey;
    private final int SANDSTORM_RADIUS;
    private final int SANDSTORM_DAMAGE;
    private final long SANDSTORM_COOLDOWN;

    public DuneTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.configManager = configManager;
        this.effectKey = new NamespacedKey(plugin, "dune_trim_effect");

        SANDSTORM_RADIUS = configManager.getInt("dune.primary.sandstorm_radius", 12);
        SANDSTORM_DAMAGE = configManager.getInt("dune.primary.sandstorm_damage", 10);
        SANDSTORM_COOLDOWN = configManager.getLong("dune.primary.cooldown", 60000);
    }


    private void createExpandingEffect(Player player) {
        // Center the effect at roughly the player's mid-body
        Location center = player.getLocation().clone().add(0, player.getEyeHeight() / 2, 0);
        World world = player.getWorld();

        // Enhanced swirling, expanding particle effect
        int layers = 15;
        double timeOffset = (System.currentTimeMillis() % 3600) / 3600.0 * 2 * Math.PI;
        for (int i = 1; i <= layers; i++) {
            double radius = i * 0.4;
            double yOffset = i * 0.15;
            int particleCount = (int)(2 * Math.PI * radius * 6);
            for (int j = 0; j < particleCount; j++) {
                double angle = (2 * Math.PI / particleCount) * j + timeOffset;
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;
                Location particleLoc = center.clone().add(offsetX, yOffset, offsetZ);
                world.spawnParticle(Particle.FALLING_DUST, particleLoc, 1, 0, 0, 0, 0, Material.SAND.createBlockData());
            }
        }

        for (int k = 0; k < 50; k++) {
            double angle = Math.random() * 2 * Math.PI;
            double distance = Math.random();
            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;
            Location burstLoc = center.clone().add(offsetX, Math.random(), offsetZ);
            world.spawnParticle(Particle.CRIT, burstLoc, 1, 0, 0, 0, 0);
        }
    }

    public void DunePrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.DUNE)) {
            return;
        }
        if (cooldownManager.isOnCooldown(player, TrimPattern.DUNE)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        Player duneUser = player; // Store the player using the ability

        // Play sound effects for activation
        world.playSound(playerLoc, Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.8f);
        world.playSound(playerLoc, Sound.ENTITY_HUSK_AMBIENT, 1.0f, 1.0f);

        // Call the enhanced expanding particle effect
        createExpandingEffect(player);

        // Additional effects:
        world.spawnParticle(Particle.FALLING_DUST, playerLoc.clone().add(0, 0.1, 0), 80, 0.7, 1.2, 0.7, 0.1, Material.SAND.createBlockData());
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, playerLoc.clone().add(0, 0.1, 0), 40, 0.7, 1.2, 0.7, 0.1);
        world.spawnParticle(Particle.CLOUD, playerLoc.clone().add(0, 1.5, 0), 60, 1.0, 0.6, 1.0, 0.1);

        for (Entity entity : world.getNearbyEntities(playerLoc, SANDSTORM_RADIUS, SANDSTORM_RADIUS, SANDSTORM_RADIUS)) {
            if (entity instanceof LivingEntity target && !target.equals(duneUser)) {
                if (target instanceof Player targetPlayer && trustManager.isTrusted(duneUser.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue; // Skip trusted players
                }
                Location targetLoc = target.getLocation();
                Vector knockbackDirection = targetLoc.toVector().subtract(playerLoc.toVector()).normalize().multiply(1.5);
                knockbackDirection.add(new Vector(0, 0.5, 0));
                target.setVelocity(knockbackDirection);

                target.damage(SANDSTORM_DAMAGE);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0)); // 3 seconds of blindness
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1)); // 4 seconds of slowness

                world.spawnParticle(Particle.FALLING_DUST, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05, Material.SAND.createBlockData());
            }
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));

        cooldownManager.setCooldown(player, TrimPattern.DUNE, SANDSTORM_COOLDOWN);
        player.sendMessage("§8[§6Dune§8] §7You have unleashed a " + ChatColor.GOLD + "Sandstorm" + ChatColor.GRAY + "!");
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (!configManager.isTrimEnabled("dune")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            DunePrimary(event.getPlayer());
        }
    }
}
