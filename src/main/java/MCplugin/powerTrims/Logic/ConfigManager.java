package MCplugin.powerTrims.Logic;

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
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        loadTrimStatus();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadTrimStatus();
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
        // Defaults to true if a trim is not specified in the config.
        return trimEnabledStatus.getOrDefault(trimName.toLowerCase(), true);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public long getLong(String path, long def) {
        return config.getLong(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }
}
