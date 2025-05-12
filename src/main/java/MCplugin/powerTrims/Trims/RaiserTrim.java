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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.util.Vector;

public class RaiserTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final NamespacedKey effectKey;
    private static final long SURGE_COOLDOWN = 120000; // 2 minutes cooldown
    private static final double ENTITY_PULL_RADIUS = 15.0;
    private static final double PLAYER_UPWARD_BOOST = 1.5;

    public RaiserTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.effectKey = new NamespacedKey(plugin, "raiser_trim_effect");
        RaiserPassive();
    }

    // Passive Ability: Ascended Ward
    private void RaiserPassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RAISER)) {
                    Location auraLoc = player.getLocation().clone().add(0, player.getEyeHeight() / 2, 0);
                    player.getWorld().spawnParticle(Particle.WITCH, auraLoc, 15, 0.5, 0.5, 0.5, 0.05);
                    player.getWorld().spawnParticle(Particle.CLOUD, auraLoc, 10, 0.5, 0.5, 0.5, 0.05);
                    player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                } else {
                    if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                        player.getPersistentDataContainer().remove(effectKey);
                    }
                }
            }
        }, 0L, 10L);
    }

    // Primary Ability:
// Launches the player upward; when they land, any entity within a 15-block radius is pulled in and launched upward with slowness.
    public void RaiserPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RAISER)) {
            return;
        }
        if (cooldownManager.isOnCooldown(player, TrimPattern.RAISER)) {
            return;
        }

        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        Player raiserUser = player; // Store the player using the ability

        // Apply Ender Pearl cooldown to nearby players immediately on ability activation
        for (Entity entity : world.getNearbyEntities(playerLoc, ENTITY_PULL_RADIUS, ENTITY_PULL_RADIUS, ENTITY_PULL_RADIUS)) {
            if (entity instanceof Player target && !target.equals(raiserUser)) {
                if (!trustManager.isTrusted(raiserUser.getUniqueId(), target.getUniqueId())) {
                    target.setCooldown(Material.ENDER_PEARL, 200); // 10 seconds (200 ticks)
                    target.sendMessage(ChatColor.DARK_PURPLE + "Raiser's Surge disrupted your teleportation!");
                }
            }
        }

        // Launch the player upward
        player.setVelocity(new Vector(0, PLAYER_UPWARD_BOOST, 0));
        player.sendMessage(ChatColor.GOLD + "Ability activated!");
        world.playSound(playerLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Delay the effect until the player lands (1 second delay)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location landingLoc = player.getLocation();

            world.playSound(landingLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

            // Pull and launch nearby entities
            for (Entity entity : world.getNearbyEntities(landingLoc, ENTITY_PULL_RADIUS, ENTITY_PULL_RADIUS, ENTITY_PULL_RADIUS)) {
                if (entity instanceof LivingEntity target && !target.equals(raiserUser)) {
                    if (target instanceof Player targetPlayer && trustManager.isTrusted(raiserUser.getUniqueId(), targetPlayer.getUniqueId())) {
                        continue; // Skip trusted players
                    }
                    Vector pull = raiserUser.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.5);
                    pull.setY(1.2); // Boost upward
                    target.setVelocity(pull);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 2)); // Slowness III for 5 seconds


                    // Apply Ender Pearl cooldown to players
                    if (target instanceof Player targetPlayer && !target.equals(raiserUser) && !trustManager.isTrusted(raiserUser.getUniqueId(), targetPlayer.getUniqueId())) {
                        targetPlayer.setCooldown(Material.ENDER_PEARL, 200); // 10 seconds (200 ticks)
                    }
                }
            }

            // Set ability cooldown
            cooldownManager.setCooldown(player, TrimPattern.RAISER, SURGE_COOLDOWN);
        }, 20L);
    }




    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL &&
                    ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RAISER)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            RaiserPrimary(player);
        }
    }
}