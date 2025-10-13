package MCplugin.powerTrims.ultimates.silenceult;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SilenceUltData {
    // --- Core Ability Constants ---
    public static final double RAGE_PER_HIT_TAKEN = 1.0;
    public static final double RAGE_PER_HIT_DEALT = 0.5;
    public static final double MAX_RAGE = 10.0;
    public static final long WARDEN_DURATION_SECONDS = 40;
    public static final int WARDEN_STRENGTH_LEVEL = 2; // Strength III
    public static final int WARDEN_HEALTH_BOOST_LEVEL = 4; // Health Boost V (8 extra hearts)
    public static final int WARDEN_RESISTANCE_LEVEL = 1; // Resistance II

    // --- Ability Specific Constants ---
    public static final long BOOM_COOLDOWN_SECONDS = 5;
    public static final int BOOM_DAMAGE = 15;
    public static final double BOOM_LENGTH = 20.0;
    public static final double BOOM_AOE_RADIUS = 3.0;

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
    public static final int LIGHTNING_STRIKES_PER_TICK = 2;
    public static final double LIGHTNING_RANDOM_OFFSET = 8.0;
    public static final boolean ENABLE_WEATHER_EFFECT = true;
    public static final double AURA_RADIUS = 10.0;
    public static final double AURA_DAMAGE = 1.0;
    public static final int PRE_DETONATION_TICKS = 30;

    public final Map<UUID, Double> rage = new ConcurrentHashMap<>();
    public final Set<UUID> transformingPlayers = ConcurrentHashMap.newKeySet();
    public final Set<UUID> leapingPlayers = ConcurrentHashMap.newKeySet();

    // --- Cooldowns and Timers ---
    public final Map<UUID, Long> wardenBoomCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, Long> deepDarkGraspCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, Long> obliteratingLeapCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> wardenTimers = new ConcurrentHashMap<>();

    // --- Centralized storage for tasks and block data ---
    public final Map<UUID, Map<Location, BlockData>> originalBlocks = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> sculkTasks = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> auraTasks = new ConcurrentHashMap<>();
    public final Map<UUID, BukkitTask> mainAnimationTasks = new ConcurrentHashMap<>();
}
