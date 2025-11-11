package MCplugin.powerTrims.ultimates.silenceult;

import MCplugin.powerTrims.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SilenceUltData {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    // --- Core Ability Constants ---
    public final double RAGE_PER_HIT_TAKEN;
    public final double RAGE_PER_HIT_DEALT;
    public final double MAX_RAGE;
    public final long WARDEN_DURATION_SECONDS;
    public final int WARDEN_STRENGTH_LEVEL;
    public final int WARDEN_HEALTH_BOOST_LEVEL;
    public final int WARDEN_RESISTANCE_LEVEL;

    // --- Ability Specific Constants ---
    public final long BOOM_COOLDOWN_SECONDS;
    public final int BOOM_DAMAGE;
    public final double BOOM_LENGTH;
    public final double BOOM_AOE_RADIUS;
    public final Set<UUID> chargingBoomPlayers = new HashSet<>();

    public final long GRASP_COOLDOWN_SECONDS;
    public final double GRASP_RADIUS;
    public final double GRASP_STRENGTH;

    public final long LEAP_COOLDOWN_SECONDS;
    public final double LEAP_POWER;
    public final double LEAP_SLAM_RADIUS;
    public final float LEAP_SLAM_EXPLOSION_POWER;
    public final boolean LEAP_SLAM_BREAKS_BLOCKS;
    public final boolean LEAP_SLAM_SETS_FIRE;

    // --- Transformation Animation Constants ---
    public final int TRANSFORM_ANIMATION_SECONDS;
    public final int SCULK_SPREAD_RADIUS;
    public final double LIGHTNING_RANDOM_OFFSET;
    public final boolean ENABLE_WEATHER_EFFECT;

    public final Map<UUID, Double> rage = new ConcurrentHashMap<>();
    public final Set<UUID> transformingPlayers = ConcurrentHashMap.newKeySet();
    public final Set<UUID> leapingPlayers = ConcurrentHashMap.newKeySet();

    // --- Cooldowns and Timers ---
    public final Map<UUID, Long> wardenBoomCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, Long> deepDarkGraspCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, Long> obliteratingLeapCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> wardenTimers = new ConcurrentHashMap<>();
    public final Map<UUID, Long> wardenEndTimes = new ConcurrentHashMap<>();

    // --- Centralized storage for tasks and block data ---
    public final Map<UUID, Map<Location, BlockData>> originalBlocks = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> sculkTasks = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> mainAnimationTasks = new ConcurrentHashMap<>();

    public SilenceUltData(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        // --- Core Ability Constants ---
        RAGE_PER_HIT_TAKEN = configManager.getDouble("silence.ult.rage_per_hit_taken");
        RAGE_PER_HIT_DEALT = configManager.getDouble("silence.ult.rage_per_hit_dealt");
        MAX_RAGE = configManager.getDouble("silence.ult.max_rage");
        WARDEN_DURATION_SECONDS = configManager.getLong("silence.ult.warden_duration_seconds");
        WARDEN_STRENGTH_LEVEL = configManager.getInt("silence.ult.warden_strength_level");
        WARDEN_HEALTH_BOOST_LEVEL = configManager.getInt("silence.ult.warden_health_boost_level");
        WARDEN_RESISTANCE_LEVEL = configManager.getInt("silence.ult.warden_resistance_level");

        // --- Ability Specific Constants ---
        BOOM_COOLDOWN_SECONDS = configManager.getLong("silence.ult.boom_cooldown_seconds");
        BOOM_DAMAGE = configManager.getInt("silence.ult.boom_damage");
        BOOM_LENGTH = configManager.getDouble("silence.ult.boom_length");
        BOOM_AOE_RADIUS = configManager.getDouble("silence.ult.boom_aoe_radius");

        GRASP_COOLDOWN_SECONDS = configManager.getLong("silence.ult.grasp_cooldown_seconds");
        GRASP_RADIUS = configManager.getDouble("silence.ult.grasp_radius");
        GRASP_STRENGTH = configManager.getDouble("silence.ult.grasp_strength");

        LEAP_COOLDOWN_SECONDS = configManager.getLong("silence.ult.leap_cooldown_seconds");
        LEAP_POWER = configManager.getDouble("silence.ult.leap_power");
        LEAP_SLAM_RADIUS = configManager.getDouble("silence.ult.leap_slam_radius");
        LEAP_SLAM_EXPLOSION_POWER = (float) configManager.getDouble("silence.ult.leap_slam_explosion_power");
        LEAP_SLAM_BREAKS_BLOCKS = configManager.getBoolean("silence.ult.leap_slam_breaks_blocks");
        LEAP_SLAM_SETS_FIRE = configManager.getBoolean("silence.ult.leap_slam_sets_fire");

        // --- Transformation Animation Constants ---
        TRANSFORM_ANIMATION_SECONDS = configManager.getInt("silence.ult.transform_animation_seconds");
        SCULK_SPREAD_RADIUS = configManager.getInt("silence.ult.sculk_spread_radius");
        LIGHTNING_RANDOM_OFFSET = configManager.getDouble("silence.ult.lightning_random_offset");
        ENABLE_WEATHER_EFFECT = configManager.getBoolean("silence.ult.enable_weather_effect");
    }

    public void saveOriginalBlocks() {
        File file = new File(plugin.getDataFolder(), "silence_ult_blocks.yml");
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, Map<Location, BlockData>> entry : originalBlocks.entrySet()) {
            String uuid = entry.getKey().toString();
            for (Map.Entry<Location, BlockData> blockEntry : entry.getValue().entrySet()) {
                String path = uuid + "." + blockEntry.getKey().toString();
                config.set(path, blockEntry.getValue().getAsString());
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save silence_ult_blocks.yml!");
            e.printStackTrace();
        }
    }

    public void loadOriginalBlocks() {
        File file = new File(plugin.getDataFolder(), "silence_ult_blocks.yml");
        if (!file.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String uuid : config.getKeys(false)) {
            Map<Location, BlockData> playerBlocks = new ConcurrentHashMap<>();
            for (String locString : Objects.requireNonNull(config.getConfigurationSection(uuid)).getKeys(false)) {
                try {
                    Location loc = Location.deserialize(Objects.requireNonNull(Objects.requireNonNull(config.getConfigurationSection(uuid)).getConfigurationSection(locString)).getValues(true));
                    BlockData blockData = Bukkit.createBlockData(config.getString(uuid + "." + locString));
                    playerBlocks.put(loc, blockData);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not deserialize block data for " + uuid + " at " + locString);
                }
            }
            originalBlocks.put(UUID.fromString(uuid), playerBlocks);
        }
    }
}
