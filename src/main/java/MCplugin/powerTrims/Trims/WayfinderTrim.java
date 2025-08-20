/*
 * This file is part of [ POWER TRIMS ].
 *
 * [POWER TRIMS] is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * [ POWER TRIMS ] is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied implied warranty of
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
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WayfinderTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final ConfigManager configManager;
    private final NamespacedKey effectKey;
    private final long TELEPORT_COOLDOWN;
    private final Map<UUID, Location> markedLocations = new HashMap<>();
    private final boolean wayfinderEnabled;

    public WayfinderTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.configManager = configManager;
        this.effectKey = new NamespacedKey(plugin, "wayfinder_trim_effect");
        this.TELEPORT_COOLDOWN = configManager.getLong("wayfinder.primary.cooldown", 120000);
        this.wayfinderEnabled = configManager.getBoolean("wayfinder.primary.enabled", true);
    }



    public void WayfinderPrimary(Player player) {

        if (!wayfinderEnabled) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WAYFINDER)) {
            return;
        }
        if (cooldownManager.isOnCooldown(player, TrimPattern.WAYFINDER)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        World world = player.getWorld();

        // Check if a marker already exists for this player
        if (markedLocations.containsKey(uuid)) {
            // Teleport back to the marked location and clear the marker
            Location mark = markedLocations.remove(uuid);
            player.teleport(mark);
            player.sendMessage(ChatColor.AQUA + "You have returned to your marked location!");
            player.playSound(mark, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            // Create a teleportation particle effect visible to all
            world.spawnParticle(Particle.PORTAL, mark, 100, 1, 1, 1, 0.5);
            // Set ability cooldown
            cooldownManager.setCooldown(player, TrimPattern.WAYFINDER, TELEPORT_COOLDOWN);
        } else {
            // Mark current location without teleporting
            Location mark = player.getLocation();
            markedLocations.put(uuid, mark);
            player.sendMessage(ChatColor.DARK_AQUA + "You have marked this location!");
            player.playSound(mark, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, mark, 50, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            WayfinderPrimary(event.getPlayer());
        }
    }
}
