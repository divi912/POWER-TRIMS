package MCplugin.powerTrims.ultimates.silenceult;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SilenceUltAttacks {

    private final JavaPlugin plugin;
    private final SilenceUltData data;

    public SilenceUltAttacks(JavaPlugin plugin, SilenceUlt silenceUlt, SilenceUltData data) {
        this.plugin = plugin;
        this.data = data;
    }

    public void tryUseWardenBoom(Player player) {
        final UUID playerUUID = player.getUniqueId();
        if (data.chargingBoomPlayers.contains(playerUUID) || data.leapingPlayers.contains(playerUUID)) {
            player.sendMessage(ChatColor.YELLOW + "You are already performing an action!");
            return;
        }

        final long currentTime = System.currentTimeMillis();
        final Long lastUsed = data.wardenBoomCooldowns.get(playerUUID);
        final long cooldownMillis = TimeUnit.SECONDS.toMillis(SilenceUltData.BOOM_COOLDOWN_SECONDS);

        if (lastUsed == null || (currentTime - lastUsed >= cooldownMillis)) {
            chargeWardenBoom(player);
        } else {
            long timeLeft = (lastUsed + cooldownMillis - currentTime) / 1000;
            player.sendMessage(ChatColor.RED + "Sonic Boom on cooldown for " + (timeLeft + 1) + " seconds!");
        }
    }

    private void chargeWardenBoom(Player player) {
        final UUID playerUUID = player.getUniqueId();
        data.chargingBoomPlayers.add(playerUUID);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 105, 1, true, false));

        final List<BlockDisplay> absorbingBlocks = new ArrayList<>();
        final int chargeDurationTicks = 100;
        final int numParticles = 30;
        final double maxRadius = 5.0;

        for (int i = 0; i < numParticles; i++) {
            BlockDisplay absorbingBlock = player.getWorld().spawn(player.getEyeLocation(), BlockDisplay.class, bd -> {
                bd.setBlock(Material.SCULK.createBlockData());
                Transformation t = bd.getTransformation();
                t.getScale().set(0.25f);
                bd.setTransformation(t);
                bd.setInterpolationDuration(3);
                bd.setInterpolationDelay(-1);
            });
            absorbingBlocks.add(absorbingBlock);
        }

        new BukkitRunnable() {
            int ticks = 0;
            final double angleStep = (2 * Math.PI) / numParticles;

            @Override
            public void run() {
                if (!player.isOnline() || !data.chargingBoomPlayers.contains(playerUUID)) {
                    absorbingBlocks.forEach(Entity::remove);
                    data.chargingBoomPlayers.remove(playerUUID);
                    if (player.isOnline()) {
                        player.sendTitle("", "", 0, 1, 0); // Clear title
                    }
                    this.cancel();
                    return;
                }

                Location center = player.getEyeLocation();

                if (ticks >= chargeDurationTicks) {
                    fireWardenBoom(player);
                    data.wardenBoomCooldowns.put(playerUUID, System.currentTimeMillis());
                    data.chargingBoomPlayers.remove(playerUUID);
                    absorbingBlocks.forEach(Entity::remove);
                    player.sendTitle("", "", 0, 1, 0);
                    this.cancel();
                    return;
                }

                int chargePercentage = (int) (((double) ticks / chargeDurationTicks) * 100);
                player.sendTitle(ChatColor.DARK_AQUA + "§lSonic Boom", ChatColor.AQUA + "Charging: " + chargePercentage + "%", 0, 10, 5);

                double currentRadius = maxRadius * (1.0 - ((double) ticks / chargeDurationTicks));
                double rotation = ticks * 0.1;

                for (int i = 0; i < absorbingBlocks.size(); i++) {
                    BlockDisplay block = absorbingBlocks.get(i);
                    if (!block.isValid()) continue;

                    double angle = (i * angleStep) + rotation;
                    double yOffset = (Math.sin((ticks + i * 5) * 0.2) * 0.5);

                    Vector offset = new Vector(Math.cos(angle) * currentRadius, yOffset, Math.sin(angle) * currentRadius);
                    block.teleport(center.clone().add(offset));
                }

                if (ticks == 0) {
                     player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1.0f);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void fireWardenBoom(Player player) {
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection();
        World world = startLoc.getWorld();
        world.playSound(startLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2, 1);

        final Set<LivingEntity> entitiesToDamage = new HashSet<>();
        for (double i = 1; i < SilenceUltData.BOOM_LENGTH; i += 1) {
            Location point = startLoc.clone().add(direction.clone().multiply(i));
            entitiesToDamage.addAll(world.getNearbyLivingEntities(point, SilenceUltData.BOOM_AOE_RADIUS));
        }
        entitiesToDamage.remove(player);

        for (LivingEntity victim : entitiesToDamage) {
            double newHealth = Math.max(0, victim.getHealth() - SilenceUltData.BOOM_DAMAGE);
            victim.setHealth(newHealth);
            victim.damage(0, player);
        }

        new BukkitRunnable() {
            double distance = 0;
            @Override
            public void run() {
                if (distance > SilenceUltData.BOOM_LENGTH) {
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
        final UUID playerUUID = player.getUniqueId();
        if (data.chargingBoomPlayers.contains(playerUUID)) {
            player.sendMessage(ChatColor.RED + "You are charging a Sonic Boom!");
            return;
        }

        final long currentTime = System.currentTimeMillis();
        final Long lastUsed = data.deepDarkGraspCooldowns.get(playerUUID);
        final long cooldownMillis = TimeUnit.SECONDS.toMillis(SilenceUltData.GRASP_COOLDOWN_SECONDS);

        if (lastUsed == null || (currentTime - lastUsed >= cooldownMillis)) {
            deepDarkGrasp(player);
            data.deepDarkGraspCooldowns.put(playerUUID, currentTime);
        } else {
            long timeLeft = (lastUsed + cooldownMillis - currentTime) / 1000;
            player.sendMessage(ChatColor.RED + "Deep Dark Grasp is on cooldown for " + (timeLeft + 1) + " seconds!");
        }
    }

    private void deepDarkGrasp(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 2.0f, 0.5f);
        world.playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2.0f, 2.0f);
        world.spawnParticle(Particle.SCULK_CHARGE_POP, center.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0);

        for (Entity entity : world.getNearbyEntities(center, SilenceUltData.GRASP_RADIUS, SilenceUltData.GRASP_RADIUS, SilenceUltData.GRASP_RADIUS)) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                playGraspingTentacle(player, (LivingEntity) entity);
            }
        }
    }

    private void playGraspingTentacle(Player player, LivingEntity target) {
        final int extendTicks = 15;
        final int pullTicks = 40;
        final List<BlockDisplay> tendrilLinks = new ArrayList<>();
        final double distance = player.getLocation().distance(target.getLocation());
        final int segmentCount = (int) (distance * 2.0);

        for (int i = 0; i < segmentCount; i++) {
            BlockDisplay link = player.getWorld().spawn(player.getEyeLocation(), BlockDisplay.class, bd -> {
                bd.setBlock(Material.SCULK.createBlockData());
                bd.setInterpolationDuration(2);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(0.3f);
                bd.setTransformation(t);
            });
            tendrilLinks.add(link);
        }

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= (extendTicks + pullTicks) || !player.isValid() || !target.isValid() || target.isDead()) {
                    this.cancel();
                    tendrilLinks.forEach(Entity::remove);
                    return;
                }

                Location playerAnchor = player.getEyeLocation().add(0, -0.5, 0);
                Location targetAnchor = target.getLocation().add(0, target.getHeight() / 2, 0);

                if (ticks <= extendTicks) {
                    double extendProgress = (double) ticks / extendTicks;
                    Vector toTarget = targetAnchor.toVector().subtract(playerAnchor.toVector());
                    for (int i = 0; i < tendrilLinks.size(); i++) {
                        BlockDisplay link = tendrilLinks.get(i);
                        if (!link.isValid()) continue;

                        double progress = (double) i / (tendrilLinks.size() - 1);
                        Location linkPos = playerAnchor.clone().add(toTarget.clone().multiply(progress * extendProgress));
                        link.teleport(linkPos);
                    }
                } else {
                    Vector pullDirection = playerAnchor.toVector().subtract(targetAnchor.toVector()).normalize();
                    target.setVelocity(pullDirection.multiply(SilenceUltData.GRASP_STRENGTH));

                    Vector toTarget = targetAnchor.toVector().subtract(playerAnchor.toVector());
                    for (int i = 0; i < tendrilLinks.size(); i++) {
                        BlockDisplay link = tendrilLinks.get(i);
                        if (!link.isValid()) continue;

                        double progress = (double) i / Math.max(1, tendrilLinks.size() - 1);
                        Location linkPos = playerAnchor.clone().add(toTarget.clone().multiply(progress));
                        link.teleport(linkPos);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }


    public void tryUseObliteratingLeap(Player player) {
        final UUID playerUUID = player.getUniqueId();
        if (data.chargingBoomPlayers.contains(playerUUID)) {
            player.sendMessage(ChatColor.RED + "You are charging a Sonic Boom!");
            return;
        }
        final long currentTime = System.currentTimeMillis();
        final Long lastUsed = data.obliteratingLeapCooldowns.get(playerUUID);
        final long cooldownMillis = TimeUnit.SECONDS.toMillis(SilenceUltData.LEAP_COOLDOWN_SECONDS);

        if (lastUsed == null || (currentTime - lastUsed >= cooldownMillis)) {
            obliteratingLeap(player);
            data.obliteratingLeapCooldowns.put(playerUUID, currentTime);
        } else {
            long timeLeft = (lastUsed + cooldownMillis - currentTime) / 1000;
            player.sendMessage(ChatColor.RED + "Obliterating Leap is on cooldown for " + (timeLeft + 1) + " seconds!");
        }
    }

    private void obliteratingLeap(Player player) {
        data.leapingPlayers.add(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_AGITATED, 2.0f, 0.5f);
        player.setVelocity(new Vector(0, SilenceUltData.LEAP_POWER, 0));

        new BukkitRunnable() {
            boolean isFalling = false;
            int ticksLived = 0;

            @Override
            public void run() {
                ticksLived++;
                if (!player.isOnline() || !data.leapingPlayers.contains(player.getUniqueId()) || ticksLived > 200) { // 10 second timeout
                    this.cancel();
                    data.leapingPlayers.remove(player.getUniqueId());
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
                    data.leapingPlayers.remove(player.getUniqueId());
                    Location groundLoc = player.getLocation();
                    World world = groundLoc.getWorld();

                    world.createExplosion(player, groundLoc, SilenceUltData.LEAP_SLAM_EXPLOSION_POWER, SilenceUltData.LEAP_SLAM_SETS_FIRE, SilenceUltData.LEAP_SLAM_BREAKS_BLOCKS);

                    new BukkitRunnable() {
                        double radius = 0;
                        @Override
                        public void run() {
                            if (radius >= SilenceUltData.LEAP_SLAM_RADIUS) {
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

                    for (Entity entity : world.getNearbyEntities(groundLoc, SilenceUltData.LEAP_SLAM_RADIUS, SilenceUltData.LEAP_SLAM_RADIUS, SilenceUltData.LEAP_SLAM_RADIUS)) {
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
