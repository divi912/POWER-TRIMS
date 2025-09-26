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
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;


public class WildTrim implements Listener {

    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final ConfigManager configManager;

    // --- CONSTANTS ---
    private final int PASSIVE_TRIGGER_HEALTH;
    private final int PASSIVE_COOLDOWN_SECONDS;
    private final long PRIMARY_COOLDOWN;
    private final double GRAPPLE_RANGE;
    private final int POISON_DURATION_TICKS;
    private final double GRAPPLE_SPEED;
    private final double ROOT_TRAP_RADIUS_XZ;
    private final double ROOT_TRAP_RADIUS_Y;
    private final int ROOT_TRAP_DURATION_TICKS;

    private final Map<UUID, Long> passiveCooldowns = new HashMap<>();
    private final Set<UUID> frozenEntities = new HashSet<>();


    public WildTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.configManager = configManager;

        PASSIVE_TRIGGER_HEALTH = configManager.getInt("wild.passive.trigger_health", 8);
        PASSIVE_COOLDOWN_SECONDS = configManager.getInt("wild.passive.cooldown_seconds", 20);
        PRIMARY_COOLDOWN = configManager.getLong("wild.primary.cooldown", 20000);
        GRAPPLE_RANGE = configManager.getDouble("wild.primary.grapple_range", 60.0);
        POISON_DURATION_TICKS = configManager.getInt("wild.primary.poison_duration_ticks", 200);
        GRAPPLE_SPEED = configManager.getDouble("wild.primary.grapple_speed", 1.8);
        ROOT_TRAP_RADIUS_XZ = configManager.getDouble("wild.passive.root_trap_radius_xz", 5);
        ROOT_TRAP_RADIUS_Y = configManager.getDouble("wild.passive.root_trap_radius_y", 3);
        ROOT_TRAP_DURATION_TICKS = configManager.getInt("wild.passive.root_trap_duration_ticks", 200);
    }



    public void WildPrimary(Player player) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WILD)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.WILD)) return;

        Location start = player.getEyeLocation();
        Vector direction = player.getLocation().getDirection().normalize();
        double range = GRAPPLE_RANGE;
        Player wildUser = player; // Store the player using the ability

        // Play sound effect for grappling hook
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.0f);

        boolean abilityUsed = false;

        // Use ray-tracing to find the exact entity in the crosshair
        RayTraceResult entityHit = player.getWorld().rayTraceEntities(
                start,
                direction,
                range,
                entity -> entity instanceof LivingEntity && entity != wildUser
        );

        // Grapple to the entity under the crosshair
        if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity targetEntity) {
            if (targetEntity instanceof Player targetPlayer && trustManager.isTrusted(wildUser.getUniqueId(), targetPlayer.getUniqueId())) {
                player.sendMessage(ChatColor.GREEN + "§8[§cWild§8] Grappling to trusted player!");
                visualizeGrapple(player, targetEntity.getLocation().add(0, 1, 0));
                smoothlyPullPlayer(player, targetEntity.getLocation().add(0, 1, 0));
            } else {
                // Grapple to the entity under the crosshair
                player.sendMessage(ChatColor.GREEN + "§8[§cWild§8] Grappling to entity!");
                visualizeGrapple(player, targetEntity.getLocation().add(0, 1, 0));
                smoothlyPullPlayer(player, targetEntity.getLocation().add(0, 1, 0));
                targetEntity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, POISON_DURATION_TICKS, 1, true, false, true));
                targetEntity.sendMessage(ChatColor.GREEN + "[§cWild§8] You have been Poisoned for " + (POISON_DURATION_TICKS/20) + " sec!");
                abilityUsed = true;
            }
        } else {
            // Grapple to the block under the crosshair if no entity is found
            Block targetBlock = player.getTargetBlockExact((int) range);
            if (targetBlock != null && !targetBlock.getType().isAir()) {
                player.sendMessage(ChatColor.GREEN + "§8[§cWild§8] Grappling to block!");
                visualizeGrapple(player, targetBlock.getLocation().add(0.5, 1, 0.5));
                smoothlyPullPlayer(player, targetBlock.getLocation().add(0.5, 1, 0.5));
                abilityUsed = true;
            } else {
                // No valid target
                player.sendMessage(ChatColor.RED + "§8[§cWild§8] No valid target found!");
            }
        }

        // Apply cooldown only if the ability was successfully used
        if (abilityUsed) {
            cooldownManager.setCooldown(player, TrimPattern.WILD, PRIMARY_COOLDOWN);
        }
    }


    // Visualize the grappling effect with particles
    private void visualizeGrapple(Player player, Location target) {
        Location start = player.getEyeLocation();
        Vector direction = target.toVector().subtract(start.toVector());
        double distance = start.distance(target);

        for (double d = 0; d < distance; d += 0.5) {
            Location particleLoc = start.clone().add(direction.clone().normalize().multiply(d));
            player.getWorld().spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
        }
    }


    private void smoothlyPullPlayer(Player player, Location target) {
        player.setGravity(false);
        player.setFallDistance(0);

        new BukkitRunnable() {
            int ticks = 0;
            final double maxSpeed = GRAPPLE_SPEED;
            boolean reachedTarget = false;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    finishGrapple();
                    return;
                }

                double distanceSquared = player.getLocation().distanceSquared(target);

                if (distanceSquared < 1.5) { // Stop earlier if close
                    reachedTarget = true;
                    finishGrapple();
                    return;
                }

                if (ticks++ > 30) { // Reduce max time from 40 to 30 ticks (1.5 sec)
                    finishGrapple();
                    return;
                }

                // Dynamically calculate pull vector for smoother motion
                Vector pullVector = target.toVector().subtract(player.getLocation().toVector());
                double length = pullVector.length();
                double speed = Math.min(maxSpeed, length * 0.28); // Adjusted speed factor

                player.setVelocity(pullVector.normalize().multiply(speed));

                // Effects
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 5, 0.2, 0.2, 0.2, 0);
                if (ticks % 5 == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.2f);
                }
            }

            private void finishGrapple() {
                player.setGravity(true);
                if (reachedTarget) {
                    player.setVelocity(new Vector(0, 0.3, 0));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                }
                this.cancel();
            }
        }.runTaskTimer(plugin, 0, 1);
    }


    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (!configManager.isTrimEnabled("wild")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            WildPrimary(event.getPlayer());
        }
    }




    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WILD)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getHealth() > PASSIVE_TRIGGER_HEALTH) return;
            if (isPassiveOnCooldown(player)) {
                player.sendMessage(ChatColor.RED + "§8[§cWild§8] Root Trap is on cooldown!");
                return;
            }

            activateRootTrap(player);
            setPassiveCooldown(player);
        }, 1L); // Small delay to avoid fake damage event issues
    }

    public void activateRootTrap(Player player) {
        if (isPassiveOnCooldown(player)) {
            player.sendMessage(ChatColor.RED + "§8[§cWild§8] Root Trap is on cooldown!");
            return;
        }

        setPassiveCooldown(player);
        player.sendMessage(ChatColor.GREEN + "§8[§cWild§8] You activated Root Trap!");

        List<LivingEntity> affectedEntities = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(ROOT_TRAP_RADIUS_XZ, ROOT_TRAP_RADIUS_Y, ROOT_TRAP_RADIUS_XZ)) {
            if (entity instanceof LivingEntity && entity != player) {
                LivingEntity target = (LivingEntity) entity;
                if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue; // Skip trusted players
                }
                affectedEntities.add(target);
                frozenEntities.add(target.getUniqueId());
                spawnVinesAround(target.getLocation());
            }
        }

        // Freeze movement
        new BukkitRunnable() {
            @Override
            public void run() {
                for (LivingEntity entity : affectedEntities) {
                    if (frozenEntities.contains(entity.getUniqueId())) {
                        entity.setVelocity(new Vector(0, 0, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5);

        // Remove vines and unfreeze after 5 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                for (LivingEntity entity : affectedEntities) {
                    frozenEntities.remove(entity.getUniqueId());
                }
                removeVines(affectedEntities);
            }
        }.runTaskLater(plugin, ROOT_TRAP_DURATION_TICKS);
    }


    private void spawnVinesAround(Location location) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location vineLoc = location.clone().add(x, 0, z);
                if (vineLoc.getBlock().getType() == Material.AIR) {
                    vineLoc.getBlock().setType(Material.VINE);
                }
            }
        }
    }

    private void removeVines(List<LivingEntity> entities) {
        for (LivingEntity entity : entities) {
            Location location = entity.getLocation();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location vineLoc = location.clone().add(x, 0, z);
                    if (vineLoc.getBlock().getType() == Material.VINE) {
                        vineLoc.getBlock().setType(Material.AIR);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenEntities.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }



    // Checks if the ability is on cooldown
    private boolean isPassiveOnCooldown(Player player) {
        return passiveCooldowns.containsKey(player.getUniqueId()) && (System.currentTimeMillis() < passiveCooldowns.get(player.getUniqueId()));
    }

    // Sets the cooldown
    private void setPassiveCooldown(Player player) {
        passiveCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (PASSIVE_COOLDOWN_SECONDS * 1000L));
    }


}