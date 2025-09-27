/**package MCplugin.powerTrims.ultimates;

import MCplugin.powerTrims.Logic.ArmourChecking;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SilenceUlt implements Listener {

    // --- Core Ability Constants ---
    private static final double RAGE_PER_HIT_TAKEN = 1.0;
    private static final double RAGE_PER_HIT_DEALT = 0.5;
    private static final double MAX_RAGE = 10.0;
    private static final long WARDEN_DURATION_SECONDS = 40;
    private static final int WARDEN_STRENGTH_LEVEL = 2; // Strength III
    private static final int WARDEN_HEALTH_BOOST_LEVEL = 4; // Health Boost V (8 extra hearts)
    private static final int WARDEN_RESISTANCE_LEVEL = 1; // Resistance II

    // --- Ability Specific Constants ---
    private static final long BOOM_COOLDOWN_SECONDS = 5;
    private static final int BOOM_DAMAGE = 15;
    private static final double BOOM_LENGTH = 20.0;
    private static final double BOOM_AOE_RADIUS = 3.0;

    private static final long GRASP_COOLDOWN_SECONDS = 15;
    private static final double GRASP_RADIUS = 25.0;
    private static final double GRASP_STRENGTH = 2.0;

    private static final long LEAP_COOLDOWN_SECONDS = 25;
    private static final double LEAP_POWER = 1.8;
    private static final double LEAP_SLAM_RADIUS = 12.0;
    private static final float LEAP_SLAM_EXPLOSION_POWER = 10.0f;
    private static final boolean LEAP_SLAM_BREAKS_BLOCKS = true;
    private static final boolean LEAP_SLAM_SETS_FIRE = true;

    // --- Domain Expansion Constants ---
    private static final int BARRAGE_PROJECTILE_COUNT = 4;
    private static final long DOMAIN_DURATION_SECONDS = 60;
    private static final double DOMAIN_RADIUS = 30.0;
    private static final double DOMAIN_LEVITATION_HEIGHT = 8.0;
    private static final long ORB_COOLDOWN_TICKS = 40; // 2 seconds
    private static final double ORB_DAMAGE = 40.0; // Increased from 10.0
    private static final float ORB_EXPLOSION_POWER = 10.0f; // Increased from 4.0f
    private static final int DOMAIN_CAST_TIME_TICKS = 60; // NEW: 3-second casting animation

    // --- Transformation Animation Constants ---
    private static final int TRANSFORM_ANIMATION_SECONDS = 6;
    private static final int SCULK_SPREAD_RADIUS = 10;
    private static final int LIGHTNING_STRIKES_PER_TICK = 2;
    private static final double LIGHTNING_RANDOM_OFFSET = 8.0;
    private static final boolean ENABLE_WEATHER_EFFECT = true;
    private static final double AURA_RADIUS = 10.0;
    private static final double AURA_DAMAGE = 1.0;
    private static final int PRE_DETONATION_TICKS = 30;

    private final JavaPlugin plugin;
    private final Map<UUID, Double> rage = new ConcurrentHashMap<>();
    private final Set<UUID> transformingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> leapingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> domainActivePlayers = ConcurrentHashMap.newKeySet();

    // --- Boss Bars ---
    private final Map<UUID, BossBar> rageBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> boomBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> graspBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> leapBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> domainDurationBar = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> orbCooldownBar = new ConcurrentHashMap<>();
    private final NamespacedKey rageBossBarKey;
    private final NamespacedKey boomBossBarKey;
    private final NamespacedKey graspBossBarKey;
    private final NamespacedKey leapBossBarKey;
    private final NamespacedKey domainDurationKey;
    private final NamespacedKey orbCooldownKey;



    // --- Cooldowns and Timers ---
    private final Map<UUID, Long> wardenBoomCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> deepDarkGraspCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> obliteratingLeapCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> oblivionOrbCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> wardenTimers = new ConcurrentHashMap<>();

    // --- Centralized storage for tasks and block data ---
    private final Map<UUID, Map<Location, BlockData>> originalBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> sculkTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> auraTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> mainAnimationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, List<BukkitTask>> domainTasks = new ConcurrentHashMap<>();

    public SilenceUlt(JavaPlugin plugin) {
        this.plugin = plugin;
        this.rageBossBarKey = new NamespacedKey(plugin, "silence_ult_rage_bar");
        this.boomBossBarKey = new NamespacedKey(plugin, "silence_ult_boom_bar");
        this.graspBossBarKey = new NamespacedKey(plugin, "silence_ult_grasp_bar");
        this.leapBossBarKey = new NamespacedKey(plugin, "silence_ult_leap_bar");
        this.domainDurationKey = new NamespacedKey(plugin, "silence_ult_domain_duration");
        this.orbCooldownKey = new NamespacedKey(plugin, "silence_ult_orb_cooldown");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startArmorCheckTask();
    }

    private void startArmorCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateRageBar(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // --- Event Handlers ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        rageBossBars.put(playerUUID, createBar(player, rageBossBarKey, "Silence Ultimate", BarColor.BLUE));
        boomBossBars.put(playerUUID, createBar(player, boomBossBarKey, "Sonic Boom", BarColor.GREEN));
        graspBossBars.put(playerUUID, createBar(player, graspBossBarKey, "Deep Dark Grasp", BarColor.GREEN));
        leapBossBars.put(playerUUID, createBar(player, leapBossBarKey, "Obliterating Leap", BarColor.GREEN));
        domainDurationBar.put(playerUUID, createBar(player, domainDurationKey, "Malevolent Shrine", BarColor.PURPLE));
        orbCooldownBar.put(playerUUID, createBar(player, orbCooldownKey, "Oblivion Orb", BarColor.GREEN));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        cleanupPlayer(playerUUID);
        rage.remove(playerUUID);
        wardenBoomCooldowns.remove(playerUUID);
        deepDarkGraspCooldowns.remove(playerUUID);
        obliteratingLeapCooldowns.remove(playerUUID);
        oblivionOrbCooldowns.remove(playerUUID);
        rageBossBars.remove(playerUUID);
        boomBossBars.remove(playerUUID);
        graspBossBars.remove(playerUUID);
        leapBossBars.remove(playerUUID);
        domainDurationBar.remove(playerUUID);
        orbCooldownBar.remove(playerUUID);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (domainActivePlayers.contains(player.getUniqueId()) || DisguiseAPI.isDisguised(player)) {
            revertFromWarden(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE)) {
            addRage(player, RAGE_PER_HIT_TAKEN);
        }
        if (event.getDamager() instanceof Player player && ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE)) {
            addRage(player, RAGE_PER_HIT_DEALT);
        }
    }

    @EventHandler
    public void onAbilityRelatedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (transformingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && leapingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            leapingPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || leapingPlayers.contains(player.getUniqueId())) return;

        int newSlot = event.getNewSlot();
        boolean isWarden = DisguiseAPI.isDisguised(player) && DisguiseAPI.getDisguise(player).getType() == DisguiseType.WARDEN;
        boolean inDomain = domainActivePlayers.contains(player.getUniqueId());

        if (inDomain) {
            if (newSlot == 5) { // Shift + 6 while in domain
                fireOblivionBarrage(player);
            }
            return; // Block other abilities while in domain
        }

        switch (newSlot) {
            case 0 -> {
                double currentRage = rage.getOrDefault(player.getUniqueId(), 0.0);
                if (currentRage >= MAX_RAGE && !isWarden && !transformingPlayers.contains(player.getUniqueId())) {
                    startTransformationSequence(player);
                }
            }
            case 1 -> { if (isWarden) tryUseWardenBoom(player); }
            case 2 -> { if (isWarden) tryUseDeepDarkGrasp(player); }
            case 3 -> { if (isWarden) tryUseObliteratingLeap(player); }
            case 4 -> { if (isWarden) tryActivateDomain(player); }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (domainActivePlayers.contains(player.getUniqueId()) && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            tryFireOblivionOrb(player);
        }
    }

    // --- Core Logic Methods ---

    private void addRage(Player player, double amount) {
        if (DisguiseAPI.isDisguised(player) || transformingPlayers.contains(player.getUniqueId())) return;
        double currentRage = rage.getOrDefault(player.getUniqueId(), 0.0);
        double newRage = Math.min(MAX_RAGE, currentRage + amount);
        rage.put(player.getUniqueId(), newRage);
        updateRageBar(player);
    }

    private void updateRageBar(Player player) {
        BossBar rageBar = rageBossBars.get(player.getUniqueId());
        if (rageBar == null || DisguiseAPI.isDisguised(player) || transformingPlayers.contains(player.getUniqueId())) return;

        if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE)) {
            rageBar.setVisible(true);
            double currentRage = rage.getOrDefault(player.getUniqueId(), 0.0);
            rageBar.setProgress(currentRage / MAX_RAGE);
            if (currentRage >= MAX_RAGE) {
                rageBar.setColor(BarColor.RED);
                rageBar.setTitle(ChatColor.RED + "" + ChatColor.BOLD + "ULTIMATE READY (Sneak + Slot 1)");
            } else {
                rageBar.setColor(BarColor.BLUE);
                rageBar.setTitle(ChatColor.DARK_AQUA + "Silence Ultimate");
            }
        } else {
            rageBar.setVisible(false);
            rage.put(player.getUniqueId(), 0.0);
        }
    }

    // --- Transformation Sequence Methods ---

    private void startTransformationSequence(Player player) {
        UUID playerUUID = player.getUniqueId();
        transformingPlayers.add(playerUUID);
        originalBlocks.put(playerUUID, new HashMap<>());
        rage.put(playerUUID, 0.0);
        updateRageBar(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, TRANSFORM_ANIMATION_SECONDS * 20, 1, false, false));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.5f);
        if (ENABLE_WEATHER_EFFECT) {
            player.setPlayerWeather(WeatherType.DOWNFALL);
        }
        startMainAnimation(player);
        startCorruptionAura(player);
        startSculkInfestation(player);
    }

    private void completeWardenTransformation(Player player) {
        if (!player.isOnline()) return;
        cleanupPlayer(player.getUniqueId());
        Location loc = player.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_WARDEN_ROAR, 3.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1, 0), 1);
        world.spawnParticle(Particle.EXPLOSION, loc, 5);

        for (Entity entity : world.getNearbyEntities(loc, 10.0, 10.0, 10.0)) {
            if (entity.equals(player) || !(entity instanceof LivingEntity)) continue;
            Vector direction = entity.getLocation().toVector().subtract(loc.toVector()).normalize();
            direction.multiply(1.5).setY(0.5);
            entity.setVelocity(direction);
        }

        for (Entity entity : world.getNearbyEntities(loc, 16, 16, 16)) {
            if (entity instanceof Player nearbyPlayer) {
                nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
            }
        }
        player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "You have embraced the deep dark!");

        MobDisguise wardenDisguise = new MobDisguise(DisguiseType.WARDEN);
        wardenDisguise.setViewSelfDisguise(true);
        wardenDisguise.setTallSelfDisguise(DisguiseConfig.TallSelfDisguise.HIDDEN);
        DisguiseAPI.disguiseToAll(player, wardenDisguise);

        long durationTicks = WARDEN_DURATION_SECONDS * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, (int) durationTicks, WARDEN_HEALTH_BOOST_LEVEL, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int) durationTicks, WARDEN_STRENGTH_LEVEL, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int) durationTicks, WARDEN_RESISTANCE_LEVEL, false, false));
        startWardenTimer(player);
    }

    private void revertFromWarden(Player player, boolean sendMessage) {
        cleanupPlayer(player.getUniqueId());
        if (sendMessage) {
            player.sendMessage(ChatColor.GRAY + "The Warden's power recedes...");
        }

        UUID playerUUID = player.getUniqueId();
        if (rageBossBars.containsKey(playerUUID)) rageBossBars.get(playerUUID).setVisible(false);
        if (boomBossBars.containsKey(playerUUID)) boomBossBars.get(playerUUID).setVisible(false);
        if (graspBossBars.containsKey(playerUUID)) graspBossBars.get(playerUUID).setVisible(false);
        if (leapBossBars.containsKey(playerUUID)) leapBossBars.get(playerUUID).setVisible(false);

        if (DisguiseAPI.isDisguised(player)) DisguiseAPI.undisguiseToAll(player);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.HEALTH_BOOST);
        updateRageBar(player);
    }

    // --- Animation Component Methods ---

    private void startMainAnimation(Player player) {
        UUID playerUUID = player.getUniqueId();
        final int totalDurationTicks = TRANSFORM_ANIMATION_SECONDS * 20;
        mainAnimationTasks.put(playerUUID, new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;
            final double maxRadius = 5.0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalDurationTicks) {
                    if (player.isOnline()) completeWardenTransformation(player);
                    this.cancel();
                    return;
                }
                Location loc = player.getLocation();
                World world = loc.getWorld();
                double progress = (double) ticks / totalDurationTicks;

                if (ticks >= totalDurationTicks - PRE_DETONATION_TICKS) {
                    if (ticks == totalDurationTicks - PRE_DETONATION_TICKS) {
                        world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 0.5f);
                        world.playSound(loc, Sound.ENTITY_WARDEN_AGITATED, 2.0f, 0.5f);
                    }
                    for (int i = 0; i < 10; i++) {
                        Vector offset = new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize().multiply(AURA_RADIUS);
                        world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(offset), 0, -offset.getX() / 10, -offset.getY() / 10, -offset.getZ() / 10, 0.5);
                    }
                    ticks++;
                    return;
                }

                for (int i = 0; i < LIGHTNING_STRIKES_PER_TICK; i++) {
                    double offsetX = (Math.random() - 0.5) * LIGHTNING_RANDOM_OFFSET * 2;
                    double offsetZ = (Math.random() - 0.5) * LIGHTNING_RANDOM_OFFSET * 2;
                    world.strikeLightningEffect(loc.clone().add(offsetX, 0, offsetZ));
                }
                if (ticks % 12 == 0) {
                    float pitch = 0.5f + (float) progress * 1.5f;
                    world.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, pitch);
                }
                player.sendTitle(ChatColor.DARK_AQUA + "ACTIVATING TRIM BODY RESONANCE", ChatColor.AQUA + "" + (int) (progress * 100) + "%", 0, 2, 0);

                angle += Math.PI / 16;
                double currentRadius = maxRadius * (1 - progress);
                for (int i = 0; i < 360; i += 10) {
                    double tiltAngle = Math.toRadians(60);
                    Vector rotationAxis = new Vector(Math.cos(angle), 0, Math.sin(angle));
                    Location particleLoc1 = loc.clone().add(new Vector(0, 1, 0));
                    Vector p1 = new Vector(currentRadius * Math.cos(Math.toRadians(i)), 0, currentRadius * Math.sin(Math.toRadians(i)));
                    rotateVector(p1, rotationAxis, tiltAngle);
                    particleLoc1.add(p1);
                    world.spawnParticle(Particle.SCULK_SOUL, particleLoc1, 1, 0, 0, 0, 0);
                    Location particleLoc2 = loc.clone().add(new Vector(0, 1, 0));
                    Vector p2 = new Vector(currentRadius * Math.cos(Math.toRadians(i)), 0, currentRadius * Math.sin(Math.toRadians(i)));
                    rotateVector(p2, rotationAxis, -tiltAngle);
                    particleLoc2.add(p2);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc2, 1, 0, 0, 0, 0);
                }

                if (ticks == totalDurationTicks - PRE_DETONATION_TICKS - 1) {
                    world.playSound(loc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 3.0f, 0.5f);
                }
                for (Entity entity : world.getNearbyEntities(loc, 16, 16, 16)) {
                    if (entity instanceof Player nearbyPlayer && !nearbyPlayer.equals(player)) {
                        Vector dir = loc.toVector().subtract(nearbyPlayer.getEyeLocation().toVector()).normalize();
                        nearbyPlayer.teleport(nearbyPlayer.getLocation().setDirection(dir));
                        nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L));
    }

    private void startCorruptionAura(Player player) {
        UUID playerUUID = player.getUniqueId();
        auraTasks.put(playerUUID, new BukkitRunnable() {
            @Override
            public void run() {
                if (!transformingPlayers.contains(playerUUID) || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                Location center = player.getLocation().add(0, 1, 0);
                for (double i = 0; i <= Math.PI; i += Math.PI / 12) {
                    for (double j = 0; j < Math.PI * 2; j += Math.PI / 12) {
                        double x = AURA_RADIUS * Math.sin(i) * Math.cos(j);
                        double y = AURA_RADIUS * Math.cos(i);
                        double z = AURA_RADIUS * Math.sin(i) * Math.sin(j);
                        center.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, center.clone().add(x, y, z), 1, 0, 0, 0, 0);
                    }
                }
                for (Entity entity : center.getWorld().getNearbyEntities(center, AURA_RADIUS, AURA_RADIUS, AURA_RADIUS)) {
                    if (entity instanceof LivingEntity && !entity.equals(player)) {
                        ((LivingEntity) entity).damage(AURA_DAMAGE / 2.0, player);
                        ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0));
                        ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L));
    }

    private void startSculkInfestation(Player player) {
        UUID playerUUID = player.getUniqueId();
        sculkTasks.put(playerUUID, new BukkitRunnable() {
            int radius = 0;
            @Override
            public void run() {
                if (!transformingPlayers.contains(playerUUID) || !player.isOnline() || radius > SCULK_SPREAD_RADIUS) {
                    this.cancel();
                    return;
                }
                Location center = player.getLocation();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.abs(x) < radius && Math.abs(z) < radius) continue;
                        Location blockLoc = center.clone().add(x, -1, z);
                        blockLoc = blockLoc.getWorld().getHighestBlockAt(blockLoc).getLocation();
                        if (blockLoc.distanceSquared(center) > SCULK_SPREAD_RADIUS * SCULK_SPREAD_RADIUS) continue;
                        Material type = blockLoc.getBlock().getType();
                        if (!type.isAir() && type.isSolid() && type != Material.SCULK) {
                            Map<Location, BlockData> playerOriginals = originalBlocks.get(player.getUniqueId());
                            if (playerOriginals != null && !playerOriginals.containsKey(blockLoc)) {
                                playerOriginals.put(blockLoc, blockLoc.getBlock().getBlockData());
                                blockLoc.getBlock().setType(Material.SCULK);
                                blockLoc.getWorld().playSound(blockLoc, Sound.BLOCK_SCULK_SPREAD, 1.0f, 1.0f);
                            }
                        }
                    }
                }
                radius++;
            }
        }.runTaskTimer(plugin, 0L, 8L));
    }

    // --- Warden Ability Methods ---

    private void tryUseWardenBoom(Player player) {
        long currentTime = System.currentTimeMillis();
        long lastUsed = wardenBoomCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMillis = TimeUnit.SECONDS.toMillis(BOOM_COOLDOWN_SECONDS);

        if (currentTime - lastUsed > cooldownMillis) {
            wardenBoom(player);
            wardenBoomCooldowns.put(player.getUniqueId(), currentTime);
        } else {
            long timeLeft = (lastUsed + cooldownMillis - currentTime) / 1000;
            player.sendMessage(ChatColor.RED + "Sonic Boom on cooldown for " + (timeLeft + 1) + " seconds!");
        }
    }

    private void wardenBoom(Player player) {
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection();
        World world = startLoc.getWorld();
        world.playSound(startLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2, 1);

        final Set<LivingEntity> entitiesToDamage = new HashSet<>();
        for (double i = 1; i < BOOM_LENGTH; i += 1) {
            Location point = startLoc.clone().add(direction.clone().multiply(i));
            entitiesToDamage.addAll(world.getNearbyLivingEntities(point, BOOM_AOE_RADIUS));
        }
        entitiesToDamage.remove(player);

        for (LivingEntity victim : entitiesToDamage) {
            double newHealth = Math.max(0, victim.getHealth() - BOOM_DAMAGE);
            victim.setHealth(newHealth);
            victim.damage(0, player);
        }

        new BukkitRunnable() {
            double distance = 0;
            @Override
            public void run() {
                if (distance > BOOM_LENGTH) {
                    this.cancel();
                    return;
                }
                Location particleLoc = startLoc.clone().add(direction.clone().multiply(distance));
                world.spawnParticle(Particle.SONIC_BOOM, particleLoc, 1, 0, 0, 0, 0);
                distance += 1.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tryUseDeepDarkGrasp(Player player) {
        long now = System.currentTimeMillis();
        long lastUsed = deepDarkGraspCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMillis = TimeUnit.SECONDS.toMillis(GRASP_COOLDOWN_SECONDS);

        if (now - lastUsed > cooldownMillis) {
            deepDarkGrasp(player);
            deepDarkGraspCooldowns.put(player.getUniqueId(), now);
        } else {
            long timeLeft = (lastUsed + cooldownMillis - now) / 1000;
            player.sendMessage(ChatColor.RED + "Deep Dark Grasp is on cooldown for " + (timeLeft + 1) + " seconds!");
        }
    }

    private void deepDarkGrasp(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 2.0f, 0.5f);
        world.playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2.0f, 2.0f);
        world.spawnParticle(Particle.SCULK_CHARGE_POP, center.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0);

        for (Entity entity : world.getNearbyEntities(center, GRASP_RADIUS, GRASP_RADIUS, GRASP_RADIUS)) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                Vector direction = center.toVector().subtract(entity.getLocation().toVector()).normalize();
                entity.setVelocity(direction.multiply(GRASP_STRENGTH));
                new BukkitRunnable() {
                    double t = 0;
                    final Vector offset = new Vector(0, 0.5, 0);
                    @Override
                    public void run() {
                        if (t > 1 || !entity.isValid() || !player.isValid() || player.getLocation().distanceSquared(entity.getLocation()) < 4) {
                            this.cancel();
                            return;
                        }
                        Vector path = player.getLocation().toVector().subtract(entity.getLocation().toVector());
                        Location particleLoc = entity.getLocation().add(path.multiply(t));
                        offset.rotateAroundY(Math.toRadians(45));
                        Location tendrilLoc = particleLoc.clone().add(offset.clone().multiply(1 - t));
                        world.spawnParticle(Particle.SCULK_SOUL, tendrilLoc.add(0,1,0), 1, 0, 0, 0, 0);
                        t += 0.05;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }
    }

    private void tryUseObliteratingLeap(Player player) {
        long now = System.currentTimeMillis();
        long lastUsed = obliteratingLeapCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMillis = TimeUnit.SECONDS.toMillis(LEAP_COOLDOWN_SECONDS);

        if (now - lastUsed > cooldownMillis) {
            obliteratingLeap(player);
            obliteratingLeapCooldowns.put(player.getUniqueId(), now);
        } else {
            long timeLeft = (lastUsed + cooldownMillis - now) / 1000;
            player.sendMessage(ChatColor.RED + "Obliterating Leap is on cooldown for " + (timeLeft + 1) + " seconds!");
        }
    }

    private void obliteratingLeap(Player player) {
        leapingPlayers.add(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_AGITATED, 2.0f, 0.5f);
        player.setVelocity(new Vector(0, LEAP_POWER, 0));

        new BukkitRunnable() {
            boolean isFalling = false;
            @Override
            public void run() {
                if (!player.isOnline() || !leapingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    leapingPlayers.remove(player.getUniqueId());
                    return;
                }

                if (player.getVelocity().getY() > 0) {
                    player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 5, 0.2, 0.2, 0.2, 0.01);
                } else {
                    player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation(), 5, 0.2, 0.2, 0.2, 0);
                }

                if (!isFalling && player.getVelocity().getY() < 0) {
                    isFalling = true;
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.0f, 1.5f);
                }


                if (isFalling) {
                    player.setVelocity(player.getVelocity().add(new Vector(0, -0.15, 0)));
                }


                if (isFalling && (player.isOnGround() || player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType().isSolid())) {
                    this.cancel();
                    Location groundLoc = player.getLocation();
                    World world = groundLoc.getWorld();


                    world.createExplosion(player, groundLoc, LEAP_SLAM_EXPLOSION_POWER, LEAP_SLAM_SETS_FIRE, LEAP_SLAM_BREAKS_BLOCKS);


                    new BukkitRunnable() {
                        double radius = 0;
                        @Override
                        public void run() {
                            if (radius >= LEAP_SLAM_RADIUS) {
                                this.cancel();
                                return;
                            }
                            radius += 0.75;
                            for (int i = 0; i < 360; i += 10) {
                                double angle = Math.toRadians(i);
                                double x = Math.cos(angle) * radius;
                                double z = Math.sin(angle) * radius;
                                Location particleLoc = groundLoc.clone().add(x, 0.2, z);
                                world.spawnParticle(Particle.BLOCK, particleLoc, 10, 0.5, 0.1, 0.5, 0, Bukkit.createBlockData(Material.DEEPSLATE));
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);

                    for (Entity entity : world.getNearbyEntities(groundLoc, LEAP_SLAM_RADIUS, LEAP_SLAM_RADIUS, LEAP_SLAM_RADIUS)) {
                        if (entity instanceof LivingEntity && !entity.equals(player)) {
                            Vector knockback = entity.getLocation().toVector().subtract(groundLoc.toVector()).normalize();
                            entity.setVelocity(knockback.multiply(2.0).setY(0.5));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void fireSingleBarrageOrb(Player shooter, Location targetLocation) {
        Location start = shooter.getEyeLocation();
        World world = start.getWorld();
        Vector direction = targetLocation.toVector().subtract(start.toVector()).normalize();

        new BukkitRunnable() {
            Location current = start.clone();
            int ticksLived = 0;
            @Override
            public void run() {
                if (ticksLived++ > 60 || !current.getBlock().isPassable() || current.distanceSquared(targetLocation) < 4) {
                    world.createExplosion(shooter, current, ORB_EXPLOSION_POWER, false, false);
                    this.cancel();
                    return;
                }
                current.add(direction);
                world.spawnParticle(Particle.WITCH, current, 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void fireOblivionBarrage(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 1.5f, 1.2f);

        List<Player> targets = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && p.getLocation().distanceSquared(center) < DOMAIN_RADIUS * DOMAIN_RADIUS) {
                targets.add(p);
            }
        }


        new BukkitRunnable() {
            int projectilesFired = 0;

            @Override
            public void run() {
                if (projectilesFired >= BARRAGE_PROJECTILE_COUNT) {
                    this.cancel();

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            revertFromWarden(player, true);
                        }
                    }.runTask(plugin);

                    return;
                }

                for (Player target : targets) {
                    if (target.isOnline()) {
                        fireSingleBarrageOrb(player, target.getEyeLocation());
                    }
                }
                projectilesFired++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void tryActivateDomain(Player player) {
        if (!wardenTimers.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You can only activate the Ultimate while in Warden form.");
            return;
        }


        startDomainCastAnimation(player);
    }

    private void startDomainCastAnimation(Player player) {
        if (wardenTimers.containsKey(player.getUniqueId())) {
            wardenTimers.get(player.getUniqueId()).cancel();
            wardenTimers.remove(player.getUniqueId());
        }
        boomBossBars.get(player.getUniqueId()).setVisible(false);
        graspBossBars.get(player.getUniqueId()).setVisible(false);
        leapBossBars.get(player.getUniqueId()).setVisible(false);

        final Location initialCenter = player.getLocation();
        final World world = initialCenter.getWorld();
        world.playSound(initialCenter, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.5f);
        world.playSound(initialCenter, Sound.ENTITY_WARDEN_AGITATED, 2.0f, 0.7f);

        if (ENABLE_WEATHER_EFFECT) {
            world.setStorm(true);
            world.setThundering(true);
            world.setThunderDuration((int) (DOMAIN_DURATION_SECONDS + 10) * 20);
        }

        BukkitTask castTask = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline() || ticks > DOMAIN_CAST_TIME_TICKS) {
                    this.cancel();
                    if (player.isOnline()) {
                        // --- NEW: Final burst and sound before activation ---
                        world.spawnParticle(Particle.EXPLOSION, player.getLocation(), 5);
                        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 3.0f, 0.5f);
                        activateDomain(player, initialCenter);
                        player.sendTitle("", ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ULTIMATE ACTIVATED!", 10, 40, 10);
                    }
                    return;
                }

                double progress = (double) ticks / DOMAIN_CAST_TIME_TICKS;
                Location targetPos = initialCenter.clone().add(0, progress * DOMAIN_LEVITATION_HEIGHT, 0);
                player.teleport(targetPos.setDirection(player.getLocation().getDirection()));
                player.setFallDistance(0);

                for (int i = 0; i < (5 + (ticks / 10)); i++) {
                    Vector offset = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5).normalize().multiply(1.5);
                    initialCenter.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, initialCenter.clone().add(offset), 0, 0, 0.1 + (progress * 0.5), 0, 0.1);
                }

                // Swirling energy vortex around the player
                double currentRadius = 3.0 * (1 - progress);
                for (int i = 0; i < 360; i += 20) {
                    double angle = Math.toRadians(i + (ticks * 5));
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(x, 1.0, z), 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(x * 0.8, 1.5, z * 0.8), 0, 0, 0, 0, 0.01);
                }

                if (ticks % 20 == 0 && ticks < DOMAIN_CAST_TIME_TICKS - 20) {
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f + (float) (progress * 1.5), 0.5f + (float) (progress * 1.0));
                }

                for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 15, 15, 15)) {
                    if (entity instanceof Player nearbyPlayer && !nearbyPlayer.equals(player)) {
                        nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
                        if (ticks % 4 == 0) {
                            nearbyPlayer.teleport(nearbyPlayer.getLocation().add(Math.random() * 0.2 - 0.1, 0, Math.random() * 0.2 - 0.1));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        domainTasks.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(castTask);
    }
    private void activateDomain(Player player, Location center) {
        UUID playerUUID = player.getUniqueId();
        domainActivePlayers.add(playerUUID);

        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 0.5f);
        world.playSound(center, Sound.ENTITY_WARDEN_AMBIENT, 2.0f, 0.5f);

        BukkitTask hoverTask = new BukkitRunnable() {
            Location hoverLocation = center.clone().add(0, DOMAIN_LEVITATION_HEIGHT, 0);
            @Override
            public void run() {
                if(!domainActivePlayers.contains(playerUUID) || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                player.teleport(hoverLocation.setDirection(player.getLocation().getDirection()));
                player.setFallDistance(0);
            }
        }.runTaskTimer(plugin, 0L, 5L);
        domainTasks.get(playerUUID).add(hoverTask);

        drawDomain(player, center);

        BossBar durationBar = domainDurationBar.get(playerUUID);
        BossBar orbBar = orbCooldownBar.get(playerUUID);
        rageBossBars.get(playerUUID).setVisible(false);
        durationBar.setVisible(true);
        orbBar.setVisible(true);

        long domainEndTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(DOMAIN_DURATION_SECONDS);

        BukkitTask domainTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (!domainActivePlayers.contains(playerUUID)) {
                    this.cancel();
                    return;
                }
                long now = System.currentTimeMillis();
                long timeLeft = domainEndTime - now;

                if (timeLeft <= 0) {
                    revertFromWarden(player, true);
                    this.cancel();
                    return;
                }

                durationBar.setProgress((double)timeLeft / TimeUnit.SECONDS.toMillis(DOMAIN_DURATION_SECONDS));
                durationBar.setTitle(ChatColor.DARK_PURPLE + "Ultimate: " + (timeLeft / 1000 + 1) + "s");

                long lastUsed = oblivionOrbCooldowns.getOrDefault(playerUUID, 0L);
                long cooldownLeft = lastUsed + (ORB_COOLDOWN_TICKS * 50) - now;
                if(cooldownLeft > 0) {
                    orbBar.setProgress((double)cooldownLeft / (ORB_COOLDOWN_TICKS * 50));
                    orbBar.setColor(BarColor.YELLOW);
                    orbBar.setTitle("Projectile Recharging");
                } else {
                    orbBar.setProgress(1.0);
                    orbBar.setColor(BarColor.GREEN);
                    orbBar.setTitle("Projectile Ready (Left Click)");
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        domainTasks.get(playerUUID).add(domainTimer);
    }

    private void drawDomain(Player player, Location center) {
        final World world = center.getWorld();

        BukkitTask domeDrawer = new BukkitRunnable() {
            private double rotationAngle = 0;

            @Override
            public void run() {
                if (!domainActivePlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }

                double pulse = Math.sin(System.currentTimeMillis() / 1000.0) * 0.5;
                double currentRadius = DOMAIN_RADIUS + pulse;
                rotationAngle += Math.PI / 128;


                for (double phi = 0; phi <= Math.PI; phi += Math.PI / 16) {
                    for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 16) {
                        double rotatedTheta = theta + rotationAngle;
                        double x = currentRadius * Math.cos(rotatedTheta) * Math.sin(phi);
                        double y = currentRadius * Math.cos(phi);
                        double z = currentRadius * Math.sin(rotatedTheta) * Math.sin(phi);
                        Location particleLoc = center.clone().add(x, y, z);


                        world.spawnParticle(Particle.LARGE_SMOKE, particleLoc, 3, 0, 0, 0, 0);
                        world.spawnParticle(Particle.SQUID_INK, particleLoc, 4, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);



        BukkitTask lightningTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!domainActivePlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                double theta = Math.random() * 2 * Math.PI;
                double phi = Math.acos(2 * Math.random() - 1);
                double x = DOMAIN_RADIUS * Math.cos(theta) * Math.sin(phi);
                double y = DOMAIN_RADIUS * Math.cos(phi);
                double z = DOMAIN_RADIUS * Math.sin(theta) * Math.sin(phi);
                world.strikeLightningEffect(center.clone().add(x, y, z));
            }
        }.runTaskTimer(plugin, 0L, 5L);

        BukkitTask sculkSpreadTask = new BukkitRunnable() {
            double currentRadius = 0;
            @Override
            public void run() {
                if (!domainActivePlayers.contains(player.getUniqueId()) || currentRadius > DOMAIN_RADIUS) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < 360; i += 5) {
                    double angle = Math.toRadians(i);
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    Location blockLoc = center.clone().add(x, -1, z);
                    blockLoc = world.getHighestBlockAt(blockLoc).getLocation();
                    Material type = blockLoc.getBlock().getType();
                    if (!type.isAir() && type.isSolid() && type != Material.SCULK) {
                        Map<Location, BlockData> playerOriginals = originalBlocks.get(player.getUniqueId());
                        if (playerOriginals != null && !playerOriginals.containsKey(blockLoc)) {
                            playerOriginals.put(blockLoc, blockLoc.getBlock().getBlockData());
                            blockLoc.getBlock().setType(Material.SCULK);
                        }
                    }
                }
                currentRadius += 1.0;
            }
        }.runTaskTimer(plugin, 10L, 4L);


        BukkitTask barrier = new BukkitRunnable() {
            @Override
            public void run() {
                if (!domainActivePlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                for (LivingEntity entity : center.getWorld().getLivingEntities()) {
                    if (entity.equals(player)) continue;
                    if (entity.getLocation().distanceSquared(center) > DOMAIN_RADIUS * DOMAIN_RADIUS && entity.getLocation().getY() < center.getY() + DOMAIN_RADIUS) {
                        Vector push = center.toVector().subtract(entity.getLocation().toVector()).normalize();
                        entity.setVelocity(push.multiply(0.5));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);

        domainTasks.get(player.getUniqueId()).addAll(Arrays.asList(lightningTask, domeDrawer, sculkSpreadTask, barrier));
    }

    private void tryFireOblivionOrb(Player player) {
        long now = System.currentTimeMillis();
        long lastUsed = oblivionOrbCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastUsed > (ORB_COOLDOWN_TICKS * 50)) {
            fireOblivionOrb(player);
            oblivionOrbCooldowns.put(player.getUniqueId(), now);
        }
    }

    private void fireOblivionOrb(Player player) {
        Location start = player.getEyeLocation();
        World world = start.getWorld();
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.5f);

        new BukkitRunnable() {
            Location current = start.clone();
            final Vector dir = start.getDirection();
            int ticksLived = 0;

            @Override
            public void run() {
                if (ticksLived++ > 60 || !current.getBlock().isPassable()) {
                    this.cancel();

                    world.createExplosion(player, current, ORB_EXPLOSION_POWER, LEAP_SLAM_SETS_FIRE, LEAP_SLAM_BREAKS_BLOCKS);

                    for (Entity entity : world.getNearbyEntities(current, ORB_EXPLOSION_POWER, ORB_EXPLOSION_POWER, ORB_EXPLOSION_POWER)) {
                        if (entity instanceof Player && !entity.equals(player)) {
                            ((LivingEntity) entity).damage(ORB_DAMAGE, player);
                        }
                    }

                    return;
                }
                current.add(dir);
                world.spawnParticle(Particle.SCULK_SOUL, current, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.SQUID_INK, current, 3, 0.1, 0.1, 0.1, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // --- Utility, UI & Cleanup Methods ---

    private void startWardenTimer(Player player) {
        UUID playerUUID = player.getUniqueId();
        BossBar durationBar = rageBossBars.get(playerUUID);
        BossBar boomBar = boomBossBars.get(playerUUID);
        BossBar graspBar = graspBossBars.get(playerUUID);
        BossBar leapBar = leapBossBars.get(playerUUID);
        if (durationBar == null || boomBar == null || graspBar == null || leapBar == null) return;

        long transformEndTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WARDEN_DURATION_SECONDS);
        durationBar.setVisible(true);
        boomBar.setVisible(true);
        graspBar.setVisible(true);
        leapBar.setVisible(true);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !DisguiseAPI.isDisguised(player) || domainActivePlayers.contains(playerUUID)) {
                    this.cancel();
                    return;
                }
                long now = System.currentTimeMillis();
                long durationLeft = transformEndTime - now;
                if (durationLeft <= 0) {
                    revertFromWarden(player, true);
                    this.cancel();
                    return;
                }
                durationBar.setProgress((double) durationLeft / TimeUnit.SECONDS.toMillis(WARDEN_DURATION_SECONDS));
                durationBar.setTitle(ChatColor.DARK_PURPLE + "Warden Form: " + (durationLeft / 1000 + 1) + "s");
                durationBar.setColor(BarColor.PURPLE);

                updateCooldownBar(boomBar, "Sonic Boom (Sneak+2)", BOOM_COOLDOWN_SECONDS, wardenBoomCooldowns.getOrDefault(playerUUID, 0L), now);
                updateCooldownBar(graspBar, "Deep Dark Grasp (Sneak+3)", GRASP_COOLDOWN_SECONDS, deepDarkGraspCooldowns.getOrDefault(playerUUID, 0L), now);
                updateCooldownBar(leapBar, "Obliterating Leap (Sneak+4)", LEAP_COOLDOWN_SECONDS, obliteratingLeapCooldowns.getOrDefault(playerUUID, 0L), now);
            }
        }.runTaskTimer(plugin, 0L, 20L);
        wardenTimers.put(player.getUniqueId(), task);
    }

    private void updateCooldownBar(BossBar bar, String name, long totalCooldown, long lastUsed, long now) {
        long cooldownMillis = TimeUnit.SECONDS.toMillis(totalCooldown);
        long cooldownLeft = lastUsed + cooldownMillis - now;
        if (cooldownLeft > 0) {
            bar.setProgress((double) cooldownLeft / cooldownMillis);
            bar.setTitle(ChatColor.YELLOW + name + ": " + (cooldownLeft / 1000 + 1) + "s");
            bar.setColor(BarColor.YELLOW);
        } else {
            bar.setProgress(1.0);
            bar.setTitle(ChatColor.GREEN + name + ": READY");
            bar.setColor(BarColor.GREEN);
        }
    }

    private void cleanupPlayer(UUID playerUUID) {
        revertSculkBlocks(playerUUID);
        originalBlocks.remove(playerUUID);
        if (sculkTasks.containsKey(playerUUID)) sculkTasks.remove(playerUUID).cancel();
        if (auraTasks.containsKey(playerUUID)) auraTasks.remove(playerUUID).cancel();
        if (mainAnimationTasks.containsKey(playerUUID)) mainAnimationTasks.remove(playerUUID).cancel();

        if (domainActivePlayers.remove(playerUUID)) {
            if(domainTasks.containsKey(playerUUID)) {
                domainTasks.get(playerUUID).forEach(BukkitTask::cancel);
                domainTasks.remove(playerUUID);
            }
            Player p = Bukkit.getPlayer(playerUUID);
            if(p != null) {

                p.removePotionEffect(PotionEffectType.LEVITATION);
                if(domainDurationBar.containsKey(playerUUID)) domainDurationBar.get(playerUUID).setVisible(false);
                if(orbCooldownBar.containsKey(playerUUID)) orbCooldownBar.get(playerUUID).setVisible(false);
            }
        }

        if (transformingPlayers.remove(playerUUID)) {
            Player p = Bukkit.getPlayer(playerUUID);
            if (p != null && ENABLE_WEATHER_EFFECT) p.resetPlayerWeather();
        }
        if (wardenTimers.containsKey(playerUUID)) wardenTimers.remove(playerUUID).cancel();
        leapingPlayers.remove(playerUUID);
    }

    private void revertSculkBlocks(UUID playerUUID) {
        Map<Location, BlockData> playerOriginals = originalBlocks.get(playerUUID);
        if (playerOriginals == null || playerOriginals.isEmpty()) return;
        for (Map.Entry<Location, BlockData> entry : playerOriginals.entrySet()) {
            if (entry.getKey().isWorldLoaded()) {
                entry.getKey().getBlock().setBlockData(entry.getValue());
            }
        }
    }

    private BossBar createBar(Player player, NamespacedKey key, String title, BarColor color) {
        BossBar bar = Bukkit.createBossBar(key, title, color, BarStyle.SOLID);
        bar.setVisible(false);
        bar.addPlayer(player);
        return bar;
    }

    private void rotateVector(Vector v, Vector axis, double angle) {
        axis.normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vector term1 = v.clone().multiply(cos);
        Vector term2 = axis.clone().crossProduct(v).multiply(sin);
        Vector term3 = axis.clone().multiply(axis.dot(v) * (1 - cos));
        v.setX(term1.getX() + term2.getX() + term3.getX());
        v.setY(term1.getY() + term2.getY() + term3.getY());
        v.setZ(term1.getZ() + term2.getZ() + term3.getZ());
    }
}**/