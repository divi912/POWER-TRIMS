package MCplugin.powerTrims.UltimateUpgrader;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RitualManager {

    private final JavaPlugin plugin;
    private final NamespacedKey upgradeKey;
    private final Map<TrimPattern, RitualConfig> ritualConfigs = new HashMap<>();
    private final Map<UUID, Ritual> activeRituals = new HashMap<>();
    private FileConfiguration upgradeCountsConfig;
    private File upgradeCountsFile;

    public RitualManager(JavaPlugin plugin, NamespacedKey upgradeKey) {
        this.plugin = plugin;
        this.upgradeKey = upgradeKey;
        loadRituals();
        loadUpgradeCounts();
    }

    public void loadRituals() {
        File ritualsFile = new File(plugin.getDataFolder(), "rituals.yml");
        if (!ritualsFile.exists()) {
            plugin.saveResource("rituals.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(ritualsFile);
        ConfigurationSection ritualsSection = config.getConfigurationSection("rituals");
        if (ritualsSection == null) return;

        for (String key : ritualsSection.getKeys(false)) {
            try {
                NamespacedKey patternKey = NamespacedKey.minecraft(key.toLowerCase());
                TrimPattern pattern = Registry.TRIM_PATTERN.get(patternKey);

                if (pattern == null) {
                    plugin.getLogger().warning("Invalid trim pattern in rituals.yml: " + key);
                    continue;
                }

                boolean enabled = ritualsSection.getBoolean(key + ".enabled", true);
                int duration = ritualsSection.getInt(key + ".duration");
                int limit = ritualsSection.getInt(key + ".limit", -1);
                List<ItemStack> materials = new ArrayList<>();
                for (String materialString : ritualsSection.getStringList(key + ".materials")) {
                    String[] parts = materialString.split(":");
                    Material material = Material.valueOf(parts[0].toUpperCase());
                    int amount = Integer.parseInt(parts[1]);
                    materials.add(new ItemStack(material, amount));
                }
                ritualConfigs.put(pattern, new RitualConfig(duration, materials, limit, enabled));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in rituals.yml for trim '" + key + "': " + e.getMessage());
            }
        }
    }

    private void loadUpgradeCounts() {
        upgradeCountsFile = new File(plugin.getDataFolder(), "upgrade_counts.yml");
        if (!upgradeCountsFile.exists()) {
            try {
                upgradeCountsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create upgrade_counts.yml!");
            }
        }
        upgradeCountsConfig = YamlConfiguration.loadConfiguration(upgradeCountsFile);
    }

    public int getUpgradeCount(TrimPattern pattern) {
        return upgradeCountsConfig.getInt(pattern.getKey().getKey(), 0);
    }

    public void incrementUpgradeCount(TrimPattern pattern) {
        int currentCount = getUpgradeCount(pattern);
        upgradeCountsConfig.set(pattern.getKey().getKey(), currentCount + 1);
        try {
            upgradeCountsConfig.save(upgradeCountsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save upgrade_counts.yml!");
        }
    }

    public RitualConfig getRitualConfig(TrimPattern pattern) {
        return ritualConfigs.get(pattern);
    }

    public boolean isPlayerInRitual(UUID playerUUID) {
        return activeRituals.containsKey(playerUUID);
    }

    public void startRitual(Player player, ItemStack[] armorSet, RitualConfig config) {
        Ritual ritual = new Ritual(plugin, player, player.getLocation(), armorSet, config, this, upgradeKey);
        activeRituals.put(player.getUniqueId(), ritual);
        ritual.start();
    }

    public void endRitual(UUID playerUUID) {
        activeRituals.remove(playerUUID);
    }
}
