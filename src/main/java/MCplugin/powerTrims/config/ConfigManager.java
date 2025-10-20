package MCplugin.powerTrims.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private final Map<String, Boolean> trimEnabledStatus = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // The config is now loaded and defaults are set in the main class before this is constructed.
        this.config = plugin.getConfig();
        loadTrimStatus();
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadTrimStatus();
        plugin.getLogger().info("Configuration reloaded.");
    }

    private void loadTrimStatus() {
        trimEnabledStatus.clear();
        ConfigurationSection trimsSection = config.getConfigurationSection("trims");
        if (trimsSection != null) {
            Set<String> trimNames = trimsSection.getKeys(false);
            for (String trimName : trimNames) {
                trimEnabledStatus.put(trimName.toLowerCase(), trimsSection.getBoolean(trimName));
            }
        }
    }

    public boolean isTrimEnabled(String trimName) {

        return trimEnabledStatus.getOrDefault(trimName.toLowerCase(), true);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public double getDouble(String path) {
        return config.getDouble(path);
    }

    public long getLong(String path) {
        return config.getLong(path);
    }
}
