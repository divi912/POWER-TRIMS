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



package MCplugin.powerTrims.Logic;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PersistentTrustManager {
    private final JavaPlugin plugin;
    private final File trustFile;
    private FileConfiguration trustConfig;
    private final Map<UUID, Set<UUID>> trustedPlayers = new HashMap<>();

    public PersistentTrustManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.trustFile = new File(plugin.getDataFolder(), "trusted_players.yml");
        loadTrusts();
    }

    private void loadTrusts() {
        if (!trustFile.exists()) {
            try {
                trustFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create trusted_players.yml!");
                e.printStackTrace();
            }
        }
        trustConfig = YamlConfiguration.loadConfiguration(trustFile);
        for (String uuidStr : trustConfig.getKeys(false)) {
            UUID ownerUUID = UUID.fromString(uuidStr);
            Set<UUID> trusted = new HashSet<>();
            if (trustConfig.getConfigurationSection(uuidStr) != null) {
                for (String trustedStr : trustConfig.getConfigurationSection(uuidStr).getKeys(false)) {
                    UUID trustedUUID = UUID.fromString(trustedStr);
                    trusted.add(trustedUUID);
                }
            }
            trustedPlayers.put(ownerUUID, trusted);
        }
    }

    public void saveTrusts() {
        // Clear existing config before saving
        for (String key : trustConfig.getKeys(false)) {
            trustConfig.set(key, null);
        }

        // Save trust relationships to the YAML file
        for (Map.Entry<UUID, Set<UUID>> entry : trustedPlayers.entrySet()) {
            String uuidStr = entry.getKey().toString();
            List<String> trustedUUIDs = new ArrayList<>();
            for (UUID trustedUUID : entry.getValue()) {
                trustedUUIDs.add(trustedUUID.toString());
            }
            trustConfig.set(uuidStr, trustedUUIDs);
        }
        try {
            trustConfig.save(trustFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save trusted_players.yml!");
            e.printStackTrace();
        }
    }

    private void sendMessage(CommandSender sender, String message, String... replacements) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                message = PlaceholderAPI.setPlaceholders(player, message);
            } else {
                for (int i = 0; i < replacements.length; i += 2) {
                    message = message.replace(replacements[i], replacements[i + 1]);
                }
            }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    public void trustPlayer(UUID player, UUID trusted, CommandSender sender) {
        Set<UUID> trustList = trustedPlayers.computeIfAbsent(player, k -> new HashSet<>());
        if (trustList.contains(trusted)) {
            sendMessage(sender, "&ePlayer is already trusted.");
            return;
        }
        trustList.add(trusted);
        Player trustedPlayer = plugin.getServer().getPlayer(trusted);
        String trustedName = (trustedPlayer != null) ? trustedPlayer.getName() : trusted.toString().substring(0, 8);
        sendMessage(sender, "&aYou have trusted " + trustedName + ".", "%player%", trustedName);
    }

    public void untrustPlayer(UUID player, UUID trusted, CommandSender sender) {
        Set<UUID> trustList = trustedPlayers.get(player);
        if (trustList == null || !trustList.contains(trusted)) {
            sendMessage(sender, "&ePlayer is not currently trusted.");
            return;
        }
        trustList.remove(trusted);
        if (trustList.isEmpty()) {
            trustedPlayers.remove(player);
        }
        Player untrustedPlayer = plugin.getServer().getPlayer(trusted);
        String untrustedName = (untrustedPlayer != null) ? untrustedPlayer.getName() : trusted.toString().substring(0, 8);
        sendMessage(sender, "&cYou have untrusted " + untrustedName + ".", "%player%", untrustedName);
    }

    public boolean isTrusted(UUID owner, UUID other) {
        return trustedPlayers.getOrDefault(owner, Collections.emptySet()).contains(other);
    }

    public Set<UUID> getTrusted(UUID player) {
        return trustedPlayers.getOrDefault(player, Collections.emptySet());
    }

    public void showTrustList(UUID player, CommandSender sender) {
        Set<UUID> trustedList = trustedPlayers.getOrDefault(player, Collections.emptySet());
        if (trustedList.isEmpty()) {
            sendMessage(sender, "&7You have no currently trusted players.");
            return;
        }
        sendMessage(sender, "&eYour trusted players:");
        for (UUID trustedUUID : trustedList) {
            Player trustedPlayer = plugin.getServer().getPlayer(trustedUUID);
            String trustedName = (trustedPlayer != null) ? trustedPlayer.getName() : trustedUUID.toString().substring(0, 8);
            sendMessage(sender, "&a- " + trustedName, "%player%", trustedName);
        }
    }
}