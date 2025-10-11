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

import MCplugin.powerTrims.Logic.*;

import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class VexTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final Map<UUID, Long> passiveCooldowns = new HashMap<>();

    // --- PRIMARY ABILITY CONSTANTS ---
    private final long PRIMARY_COOLDOWN;
    private final double PRIMARY_RADIUS;
    private final double PRIMARY_DAMAGE;
    private final int PRIMARY_DEBUFF_DURATION;
    private final int PRIMARY_BLINDNESS_DURATION;

    // --- PASSIVE ABILITY CONSTANTS ---
    private final long PASSIVE_COOLDOWN;
    private final long PASSIVE_HIDE_DURATION_TICKS;
    private final double PASSIVE_HEALTH_THRESHOLD;

    public VexTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.configManager = configManager;
        this.abilityManager = abilityManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);


        // Load Primary Ability values
        PRIMARY_COOLDOWN = configManager.getLong("vex.primary.cooldown");
        PRIMARY_RADIUS = configManager.getDouble("vex.primary.radius");
        PRIMARY_DAMAGE = configManager.getDouble("vex.primary.damage");
        PRIMARY_DEBUFF_DURATION = configManager.getInt("vex.primary.debuff_duration_ticks");
        PRIMARY_BLINDNESS_DURATION = configManager.getInt("vex.primary.blindness_duration_ticks");

        // Load Passive Ability values
        PASSIVE_COOLDOWN = configManager.getLong("vex.passive.cooldown");
        PASSIVE_HIDE_DURATION_TICKS = configManager.getLong("vex.passive.hide_duration_ticks");
        PASSIVE_HEALTH_THRESHOLD = configManager.getDouble("vex.passive.health_threshold");

        abilityManager.registerPrimaryAbility(TrimPattern.VEX, this::VexPrimary);
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

    public void VexPrimary(Player player) {
        if (!configManager.isTrimEnabled("vex")) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.VEX)) return;

        // Ability parameters
        final double radius = PRIMARY_RADIUS;
        final int steps = 30;
        final long interval = 2L;
        final int points = 72;

        Location center = player.getLocation().clone().add(0, 0.1, 0);
        Player vexUser = player; // Store the player using the ability

        // Play sound
        player.getWorld().playSound(center, Sound.ENTITY_VEX_CHARGE, 1.0f, 1.0f);

        new BukkitRunnable() {
            int i = 1;
            @Override
            public void run() {
                double r = (radius / steps) * i;
                double y = center.getY();
                for (int j = 0; j < 360; j += 360 / points) {
                    double rad = Math.toRadians(j);
                    double x = center.getX() + r * Math.cos(rad);
                    double z = center.getZ() + r * Math.sin(rad);
                    center.getWorld().spawnParticle(
                            Particle.WITCH,
                            x, y, z,
                            3,
                            0.1, 0.1, 0.1,
                            0,
                            null
                    );
                }
                if (++i > steps) {
                    this.cancel();
                    // After effect, apply damage and debuffs
                    for (LivingEntity target : center.getWorld().getNearbyLivingEntities(center, radius, radius, radius)) {
                        if (target.equals(vexUser)) continue;
                        if (target instanceof Player targetPlayer && trustManager.isTrusted(vexUser.getUniqueId(), targetPlayer.getUniqueId())) {
                            continue; // Skip trusted players
                        }
                        target.damage(PRIMARY_DAMAGE, vexUser);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, PRIMARY_DEBUFF_DURATION, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PRIMARY_DEBUFF_DURATION, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PRIMARY_DEBUFF_DURATION, 0));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PRIMARY_BLINDNESS_DURATION, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, interval);

        cooldownManager.setCooldown(player, TrimPattern.VEX, PRIMARY_COOLDOWN);
        Messaging.sendTrimMessage(player, "Vex", ChatColor.RED, "Vex's Vengeance unleashed in a " + (int)radius + "-block radius!");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) {

            // Check if health after damage is BELOW 4 hearts (8 HP)
            double newHealth = player.getHealth() - event.getFinalDamage();
            if (newHealth <= 0) return; // Prevent triggering on death
            if (newHealth >= PASSIVE_HEALTH_THRESHOLD) return; // Ensure ability activates below 4 hearts

            // Check if ability is on cooldown
            if (isPassiveOnCooldown(player)) return;

            // Activate ability
            activatePassiveAbility(player);
        }
    }

    private void activatePassiveAbility(Player player) {
        // Set the cooldown FIRST
        passiveCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + PASSIVE_COOLDOWN);

        // Hide the player from others
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.hidePlayer(plugin, player);
            }
        }

        Messaging.sendTrimMessage(player, "Vex", ChatColor.DARK_GRAY, "You have become invisible for " + (PASSIVE_HIDE_DURATION_TICKS / 20) + " seconds!");

        // Reveal the player after 10 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.showPlayer(plugin, player);
                }
                Messaging.sendTrimMessage(player, "Vex", ChatColor.GREEN, "You are now visible again!");
            }
        }.runTaskLater(plugin, PASSIVE_HIDE_DURATION_TICKS);
    }

    private boolean isPassiveOnCooldown(Player player) {
        return passiveCooldowns.containsKey(player.getUniqueId()) &&
                passiveCooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
    }
}
