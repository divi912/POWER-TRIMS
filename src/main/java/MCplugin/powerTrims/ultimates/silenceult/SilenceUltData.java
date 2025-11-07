package MCplugin.powerTrims.ultimates.silenceult;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SilenceUltData {
    private final JavaPlugin plugin;

    // --- Core Ability Constants ---
    public static final double RAGE_PER_HIT_TAKEN = 1.0;
    public static final double RAGE_PER_HIT_DEALT = 0.5;
    public static final double MAX_RAGE = 150.0;
    public static final long WARDEN_DURATION_SECONDS = 40;
    public static final int WARDEN_STRENGTH_LEVEL = 2; // Strength III
    public static final int WARDEN_HEALTH_BOOST_LEVEL = 4; // Health Boost V (8 extra hearts)
    public static final int WARDEN_RESISTANCE_LEVEL = 1; // Resistance II

    // --- Ability Specific Constants ---
    public static final long BOOM_COOLDOWN_SECONDS = 10;
    public static final int BOOM_DAMAGE = 12;
    public static final double BOOM_LENGTH = 20.0;
    public static final double BOOM_AOE_RADIUS = 3.0;
    public final Set<UUID> chargingBoomPlayers = new HashSet<>();

    public static final long GRASP_COOLDOWN_SECONDS = 15;
    public static final double GRASP_RADIUS = 25.0;
    public static final double GRASP_STRENGTH = 2.0;

    public static final long LEAP_COOLDOWN_SECONDS = 25;
    public static final double LEAP_POWER = 1.8;
    public static final double LEAP_SLAM_RADIUS = 12.0;
    public static final float LEAP_SLAM_EXPLOSION_POWER = 10.0f;
    public static final boolean LEAP_SLAM_BREAKS_BLOCKS = true;
    public static final boolean LEAP_SLAM_SETS_FIRE = true;

    // --- Transformation Animation Constants ---
    public static final int TRANSFORM_ANIMATION_SECONDS = 6;
    public static final int SCULK_SPREAD_RADIUS = 10;
    public static final double LIGHTNING_RANDOM_OFFSET = 8.0;
    public static final boolean ENABLE_WEATHER_EFFECT = true;

    public final Map<UUID, Double> rage = new ConcurrentHashMap<>();
    public final Set<UUID> transformingPlayers = ConcurrentHashMap.newKeySet();
    public final Set<UUID> leapingPlayers = ConcurrentHashMap.newKeySet();

    // --- Cooldowns and Timers ---
    public final Map<UUID, Long> wardenBoomCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, Long> deepDarkGraspCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, Long> obliteratingLeapCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> wardenTimers = new ConcurrentHashMap<>();
    public final Map<UUID, Long> wardenEndTimes = new ConcurrentHashMap<>(); // ADDED

    // --- Centralized storage for tasks and block data ---
    public final Map<UUID, Map<Location, BlockData>> originalBlocks = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> sculkTasks = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> mainAnimationTasks = new ConcurrentHashMap<>();

    public SilenceUltData(JavaPlugin plugin) {
        this.plugin = plugin;
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
            for (String locString : config.getConfigurationSection(uuid).getKeys(false)) {
                try {
                    Location loc = Location.deserialize(config.getConfigurationSection(uuid).getConfigurationSection(locString).getValues(true));
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
