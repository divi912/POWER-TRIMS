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
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlowTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final NamespacedKey effectKey;
    private static final int DASH_DURATION = 40;
    private static final long DASH_COOLDOWN = 60000; // 1 minute
    private final Map<UUID, BukkitRunnable> activeDashTasks = new HashMap<>();
    private final Map<UUID, Boolean> isDashing = new HashMap<>(); // Track if the player is dashing

    public FlowTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.effectKey = new NamespacedKey(plugin, "flow_trim_effect");
        FlowPassive();
    }

    private void FlowPassive() {
        // Passive Ability: Grants Speed II while wearing full Flow Trim Armor.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {

                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.FLOW)) {
                    if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false, true));
                        player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                    }
                } else {
                    if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                        player.removePotionEffect(PotionEffectType.SPEED);
                        player.getPersistentDataContainer().remove(effectKey);
                    }
                }
            }
        }, 0L, 20L);
    }

    public void FlowPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.FLOW)) {
            return;
        }

        if (cooldownManager.isOnCooldown(player, TrimPattern.FLOW)) {
            return;
        }

        // Cancel any existing Gale Dash task for this player
        if (activeDashTasks.containsKey(player.getUniqueId())) {
            activeDashTasks.get(player.getUniqueId()).cancel();
        }

        // Visual and sound effects for activation
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        world.playSound(playerLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.5f);

        // Create wind particle effects to signal the dash activation
        createWindEffect(player);

        // Start Gale Dash effect: propelling the player forward over a short duration.
        BukkitRunnable dashTask = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= DASH_DURATION || !player.isOnline()) {
                    cancel();
                    activeDashTasks.remove(player.getUniqueId());
                    isDashing.put(player.getUniqueId(), false); // Stop the dash flag
                    return;
                }

                // Propel the player in the direction they are facing.
                Vector direction = player.getLocation().getDirection().normalize();
                player.setVelocity(direction.multiply(1.2));

                // Create trailing wind particles along the dash.
                createWindEffect(player);
                ticks++;
            }
        };

        // Set the player as dashing
        isDashing.put(player.getUniqueId(), true);
        dashTask.runTaskTimer(plugin, 0L, 1L);
        activeDashTasks.put(player.getUniqueId(), dashTask);

        // Set cooldown and notify the player.
        cooldownManager.setCooldown(player, TrimPattern.FLOW, DASH_COOLDOWN);
        player.sendMessage("§8[§bFlow§8] §7You have activated " + ChatColor.AQUA + "Gale Dash" + ChatColor.GRAY + "!");
    }

    private void createWindEffect(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Create a circular pattern of cloud particles behind the player.
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            double offsetX = Math.cos(angle) * 0.5;
            double offsetZ = Math.sin(angle) * 0.5;
            Location particleLoc = loc.clone().add(-offsetX, 0.5, -offsetZ);
            world.spawnParticle(Particle.CLOUD, particleLoc, 0, 0, 0, 0, 0.05);
        }

        // Central burst effect using a subtle magic particle for additional flair.
        world.spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            FlowPrimary(player);
        }
    }

    @EventHandler
    public void onEntityFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && isDashing.getOrDefault(player.getUniqueId(), false)) {
                event.setCancelled(true); // Cancel fall damage if the player is dashing
            }
        }
    }
}
