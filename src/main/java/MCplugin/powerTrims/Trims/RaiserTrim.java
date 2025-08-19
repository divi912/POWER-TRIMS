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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RaiserTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;

    // --- CONSTANTS ---
    private static final long SURGE_COOLDOWN = 120000; // 2 minutes
    private static final double ENTITY_PULL_RADIUS = 15.0;
    private static final double PLAYER_UPWARD_BOOST = 1.5;
    private final int activationSlot;
    private static final int PEARL_COOLDOWN_TICKS = 200; // 10 seconds

    // --- STATE MANAGEMENT ---
    // This set will track players who are currently in the air from this ability
    private final Set<UUID> awaitingLanding = new HashSet<>();

    public RaiserTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.activationSlot = plugin.getConfig().getInt("activation-slot", 8);
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        if (event.getNewSlot() == activationSlot && event.getPlayer().isSneaking()) {
            activateRaiserPrimary(event.getPlayer());
        }
    }

    /**
     * Primary Ability: Launches the player upward and tags them for a landing effect.
     */
    public void activateRaiserPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RAISER) ||
                cooldownManager.isOnCooldown(player, TrimPattern.RAISER)) {
            return;
        }

        // Apply cooldown immediately
        cooldownManager.setCooldown(player, TrimPattern.RAISER, SURGE_COOLDOWN);

        // Launch the player upward
        player.setVelocity(new Vector(0, PLAYER_UPWARD_BOOST, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GOLD + "Raiser's Surge activated!");

        // Tag the player as awaiting their landing
        awaitingLanding.add(player.getUniqueId());

        // Failsafe task: If the player never lands (e.g., logs out, flies away),
        // remove them from the set after 5 seconds to prevent a memory leak.
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                awaitingLanding.remove(player.getUniqueId()), 100L);
    }

    /**
     * RELIABILITY IMPROVEMENT: Detects when the player lands to trigger the effect.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only proceed if the player is in the air from the ability AND is now on the ground.
        if (player.isOnGround() && awaitingLanding.remove(player.getUniqueId())) {
            triggerLandingEffect(player);
        }
    }

    /**
     * Contains all the logic for the ground-slam effect.
     */
    private void triggerLandingEffect(Player player) {
        Location landingLoc = player.getLocation();
        World world = player.getWorld();

        world.playSound(landingLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        // Pull and launch nearby entities
        // OPTIMIZATION: Use getNearbyLivingEntities to ignore items, arrows, etc.
        for (LivingEntity target : world.getNearbyLivingEntities(landingLoc, ENTITY_PULL_RADIUS)) {
            if (target.equals(player)) continue;

            if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                continue; // Skip trusted players
            }

            // Calculate pull vector and launch them
            Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.5);
            pull.setY(1.2); // Boost upward
            target.setVelocity(pull);

            // Apply effects
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 2));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0));

            if (target instanceof Player targetPlayer) {
                targetPlayer.setCooldown(Material.ENDER_PEARL, PEARL_COOLDOWN_TICKS);
                targetPlayer.sendMessage(ChatColor.DARK_PURPLE + "Raiser's Surge disrupted your teleportation!");
            }
        }
    }

    /**
     * Passive Ability: Negates fall damage.
     * OPTIMIZATION: Removed the duplicate event handler.
     */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL &&
                ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RAISER)) {
            event.setCancelled(true);
        }
    }
}