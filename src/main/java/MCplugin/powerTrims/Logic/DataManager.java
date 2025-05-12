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

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataManager {
    private final JavaPlugin plugin;
    private final File cooldownFile;

    private FileConfiguration cooldownConfig;
    private final Map<UUID, Map<String, Long>> cooldownCache;
    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        this.cooldownCache = new HashMap<>();

        loadConfigs();
    }



    private void loadConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }


        // Set up cooldown file
        if (!cooldownFile.exists()) {
            try {
                cooldownFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create cooldowns.yml!");
                e.printStackTrace();
            }
        }


        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);

        loadData();
    }

    private void loadData() {

        // Load cooldowns
        for (String uuidStr : cooldownConfig.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<String, Long> playerCooldowns = new HashMap<>();

            if (cooldownConfig.getConfigurationSection(uuidStr) != null) {
                for (String trimName : cooldownConfig.getConfigurationSection(uuidStr).getKeys(false)) {
                    long cooldown = cooldownConfig.getLong(uuidStr + "." + trimName);
                    playerCooldowns.put(trimName, cooldown);
                }
            }
            cooldownCache.put(uuid, playerCooldowns);
        }

    }

    public void saveData() {
        // Save cooldowns
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldownCache.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, Long> cooldownEntry : entry.getValue().entrySet()) {
                cooldownConfig.set(uuidStr + "." + cooldownEntry.getKey(), cooldownEntry.getValue());
            }
        }



        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data files!");
            e.printStackTrace();
        }


        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data files!");
            e.printStackTrace();
        }
    }



    // Cooldown methods
    public void setCooldown(Player player, TrimPattern pattern, long cooldown) {
        String trimName = getTrimName(pattern);
        cooldownCache.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(trimName, cooldown);
        saveData();
    }

    public long getCooldown(Player player, TrimPattern pattern) {
        String trimName = getTrimName(pattern);
        return cooldownCache.getOrDefault(player.getUniqueId(), new HashMap<>()).getOrDefault(trimName, 0L);
    }

    private String getTrimName(TrimPattern pattern) {
        if (pattern == TrimPattern.SILENCE) return "SILENCE";
        else if (pattern == TrimPattern.SPIRE) return "SPIRE";
        else if (pattern == TrimPattern.SENTRY) return "SENTRY";
        else if (pattern == TrimPattern.WILD) return "WILD";
        else if (pattern == TrimPattern.WARD) return "WARD";
        else if (pattern == TrimPattern.EYE) return "EYE";
        else if (pattern == TrimPattern.VEX) return "VEX";
        else if (pattern == TrimPattern.DUNE) return "DUNE";
        else if (pattern == TrimPattern.WAYFINDER) return "WAYFINDER";
        else if (pattern == TrimPattern.FLOW) return "FLOW";
        else if (pattern == TrimPattern.RIB) return "RIB";
        else if (pattern == TrimPattern.TIDE) return "TIDE";
        else if (pattern == TrimPattern.COAST) return "COAST";
        else if (pattern == TrimPattern.HOST) return "HOST";
        else if (pattern == TrimPattern.RAISER) return "RAISER";
        else if (pattern == TrimPattern.SNOUT) return "SNOUT";
        else return pattern.toString();
    }


}
