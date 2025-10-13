package MCplugin.powerTrims.ultimates.silenceult;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SilenceUltAttacks {

    private final JavaPlugin plugin;
    private final SilenceUltData data;

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
    private final Set<UUID> leapingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> wardenBoomCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> deepDarkGraspCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> obliteratingLeapCooldowns = new ConcurrentHashMap<>();


    public SilenceUltAttacks(JavaPlugin plugin, SilenceUlt silenceUlt, SilenceUltData data) {
        this.plugin = plugin;
        this.data = data;
    }

    public void tryUseWardenBoom(Player player) {
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

    public void wardenBoom(Player player) {
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

    public void tryUseDeepDarkGrasp(Player player) {
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

    public void deepDarkGrasp(Player player) {
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

    public void tryUseObliteratingLeap(Player player) {
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

    public void obliteratingLeap(Player player) {
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
}
