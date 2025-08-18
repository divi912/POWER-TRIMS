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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WardTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final NamespacedKey effectKey;
    // --- CONSTANTS ---
    private static final int BARRIER_DURATION = 200; // 10 seconds
    private static final int ABSORPTION_LEVEL = 4; // Absorption V (increased from III)
    private static final int RESISTANCE_BOOST_LEVEL = 2; // Resistance III (increased from II)
    private final Set<UUID> activeBarriers = new HashSet<>();

    private static final Component PREFIX = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Ward", NamedTextColor.YELLOW))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .build();


    public WardTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.effectKey = new NamespacedKey(plugin, "ward_trim_effect");
    }




    public void WardPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WARD)) {
            return;
        }

        if (cooldownManager.isOnCooldown(player, TrimPattern.WARD)) {
            return;
        }

        // Visual and sound effects
        Location playerLoc = player.getLocation();
        player.getWorld().playSound(playerLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        player.getWorld().playSound(playerLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        player.getWorld().playSound(playerLoc, Sound.BLOCK_ANVIL_USE, 0.7f, 1.5f);

        // Apply enhanced protection effects to the player
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, BARRIER_DURATION, ABSORPTION_LEVEL, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, BARRIER_DURATION, RESISTANCE_BOOST_LEVEL, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, BARRIER_DURATION, 0, true, true, true));

        // Create barrier particle effect
        createBarrierEffect(player);

        // Add player to active barriers set
        activeBarriers.add(player.getUniqueId());

        // Schedule barrier removal
        new BukkitRunnable() {
            @Override
            public void run() {
                activeBarriers.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, BARRIER_DURATION);

        player.sendMessage(Component.text()
                .append(PREFIX)
                .append(Component.text(" You have activated your personal Protective Barrier!", NamedTextColor.GOLD))
                .build());
        cooldownManager.setCooldown(player, TrimPattern.WARD, 120000); // 2 minute cooldown

        // Create continuous particle effect around the player
        new BukkitRunnable() {
            int remainingTicks = BARRIER_DURATION;

            @Override
            public void run() {
                if (remainingTicks <= 0 || !activeBarriers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }

                Location loc = player.getLocation();
                World world = player.getWorld();

                // Create a spiral effect around the player
                double y = 0;
                for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
                    double x = Math.cos(i + (remainingTicks * 0.1)) * 1.2;
                    double z = Math.sin(i + (remainingTicks * 0.1)) * 1.2;
                    Location particleLoc = loc.clone().add(x, y, z);
                    world.spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                    y += 0.2;
                    if (y > 2) y = 0;
                }

                remainingTicks--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createBarrierEffect(Player player) {
        Location center = player.getLocation();
        World world = player.getWorld();

        // Create dome effect
        new BukkitRunnable() {
            double phi = 0;

            @Override
            public void run() {
                phi += Math.PI / 8;
                if (phi >= Math.PI) {
                    this.cancel();
                    return;
                }

                for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 8) {
                    double x = 1.5 * Math.sin(phi) * Math.cos(theta);
                    double y = 1.5 * Math.cos(phi) + 1;
                    double z = 1.5 * Math.sin(phi) * Math.sin(theta);

                    Location particleLoc = center.clone().add(x, y, z);
                    world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            WardPrimary(player);
        }
    }
}