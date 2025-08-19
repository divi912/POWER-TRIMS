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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class VexTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_TIME = 120000; // 20 seconds cooldown
    private static final long HIDE_DURATION = 10000; // 10 seconds hide duration
    private static VexTrim instance;
    private final int activationSlot;



    private BukkitRunnable passiveTask;

    public VexTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        instance = this;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.activationSlot = plugin.getConfig().getInt("activation-slot", 8);
    }

    public static VexTrim getInstance() {
        return instance;
    }


    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        if (event.getNewSlot() == activationSlot && event.getPlayer().isSneaking()) {
            VexPrimary(event.getPlayer());
        }
    }

    public void VexPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.VEX)) return;

        // Ability parameters
        final double radius = 30.0;
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
                        target.damage(8.0, vexUser);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 300, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 0));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, interval);

        cooldownManager.setCooldown(player, TrimPattern.VEX, COOLDOWN_TIME);
        player.sendMessage("§8[§cVex§8] §7Vex's Vengeance unleashed in a 30-block radius!");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) {

            // Check if health after damage is BELOW 4 hearts (8 HP)
            double newHealth = player.getHealth() - event.getFinalDamage();
            if (newHealth <= 0) return; // Prevent triggering on death
            if (newHealth >= 8) return; // Ensure ability activates below 4 hearts

            // Check if ability is on cooldown
            if (isOnCooldown(player)) return;

            // Activate ability
            activateAbility(player);
        }
    }

    private void activateAbility(Player player) {
        // Set the cooldown FIRST
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + COOLDOWN_TIME);

        // Hide the player from others
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.hidePlayer(plugin, player);
            }
        }

        player.sendMessage(ChatColor.DARK_GRAY + "You have become invisible for 10 seconds!");

        // Reveal the player after 10 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.showPlayer(plugin, player);
                }
                player.sendMessage(ChatColor.GREEN + "You are now visible again!");
            }
        }.runTaskLater(plugin, HIDE_DURATION / 50); // Convert milliseconds to ticks
    }

    private boolean isOnCooldown(Player player) {
        return cooldowns.containsKey(player.getUniqueId()) &&
                cooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
    }
}