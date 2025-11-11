package MCplugin.powerTrims.ultimates.silenceult;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SilenceTransformAnimations {

    private final JavaPlugin plugin;
    private final SilenceUlt silenceUlt;
    private final SilenceUltData data;
    private final Random random = new Random();

    public SilenceTransformAnimations(JavaPlugin plugin, SilenceUlt silenceUlt, SilenceUltData data) {
        this.plugin = plugin;
        this.silenceUlt = silenceUlt;
        this.data = data;
    }

    public void startTransformationSequence(Player player) {
        UUID playerUUID = player.getUniqueId();
        data.transformingPlayers.add(playerUUID);
        data.originalBlocks.put(playerUUID, new HashMap<>());
        data.rage.put(playerUUID, 0.0);

        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, (data.TRANSFORM_ANIMATION_SECONDS + 5) * 20, 0, false, false));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.5f);
        if (data.ENABLE_WEATHER_EFFECT) {
            player.setPlayerWeather(WeatherType.DOWNFALL);
        }

        startPreAnimation(player);
        startSculkInfestation(player);
    }

    private void startPreAnimation(Player player) {
        final int preAnimationDuration = 100; // 5 seconds
        final World world = player.getWorld();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !data.transformingPlayers.contains(player.getUniqueId()) || ticks >= preAnimationDuration) {
                    if (player.isOnline()) {
                        startMainAnimation(player);
                    }
                    this.cancel();
                    return;
                }

                // Removed global world time modification

                if (ticks % 10 == 0) { // Reduced frequency of lightning effects
                    double offsetX = (random.nextDouble() - 0.5) * data.LIGHTNING_RANDOM_OFFSET * 4;
                    double offsetZ = (random.nextDouble() - 0.5) * data.LIGHTNING_RANDOM_OFFSET * 4;
                    world.strikeLightningEffect(player.getLocation().clone().add(offsetX, 0, offsetZ));
                }

                // Apply darkness to nearby players instead of forced eyesight manipulation
                if (ticks % 4 == 0) {
                    lockNearbyPlayerEyesight(player);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void startMainAnimation(Player player) {
        UUID playerUUID = player.getUniqueId();
        final int totalDurationTicks = data.TRANSFORM_ANIMATION_SECONDS * 20;
        final List<BlockDisplay> absorbingBlocks = new ArrayList<>();
        final List<BlockDisplay> shellBlocks = new ArrayList<>();
        final List<Vector> shellOffsets = createPlayerShellOffsets();

        for (Vector offset : shellOffsets) {
            Location spawnLoc = player.getLocation().clone().add(offset.clone().multiply(3.0));
            BlockDisplay shellBlock = player.getWorld().spawn(spawnLoc, BlockDisplay.class, bd -> {
                bd.setBlock(Material.SCULK.createBlockData());
                Transformation t = bd.getTransformation();
                t.getScale().set(0.01f);
                bd.setTransformation(t);
            });
            shellBlocks.add(shellBlock);
        }

        data.mainAnimationTasks.put(playerUUID, new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;
            final double maxRadius = 12.0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalDurationTicks) {
                    if (player.isOnline()) {
                        silenceUlt.completeWardenTransformation(player);
                    }
                    absorbingBlocks.forEach(Entity::remove);
                    shellBlocks.forEach(Entity::remove);
                    this.cancel();
                    return;
                }

                Location loc = player.getLocation();
                World world = loc.getWorld();
                double progress = (double) ticks / totalDurationTicks;

                // Apply darkness to nearby players instead of forced eyesight manipulation
                if (ticks % 4 == 0) {
                    lockNearbyPlayerEyesight(player);
                }

                // --- Sculk Shell Animation ---
                double shellGrowthFactor = Math.sin(progress * Math.PI / 2);

                for (int i = 0; i < shellBlocks.size(); i++) {
                    BlockDisplay shellBlock = shellBlocks.get(i);
                    if (!shellBlock.isValid()) continue;

                    Vector baseOffset = shellOffsets.get(i);
                    Vector currentOffset = baseOffset.clone().multiply(shellGrowthFactor);
                    Location targetPos = player.getLocation().clone().add(currentOffset);

                    Vector toTarget = targetPos.toVector().subtract(shellBlock.getLocation().toVector()).multiply(0.15);
                    shellBlock.teleport(shellBlock.getLocation().add(toTarget));

                    Transformation t = shellBlock.getTransformation();
                    float finalScale = 0.4f;
                    t.getScale().set((float) (shellGrowthFactor * finalScale));
                    shellBlock.setTransformation(t);
                }


                // --- Layered Absorbing Blocks Animation ---
                angle += Math.PI / 12;
                double currentRadius = maxRadius * (1 - progress);
                if (ticks % 2 == 0 && ticks < totalDurationTicks - 10) {
                    for (int i = 0; i < 10; i++) {
                        double spawnAngle = (Math.PI * 2 / 10) * i + angle + (random.nextDouble() - 0.5);
                        Location spawnLoc = loc.clone().add(Math.cos(spawnAngle) * currentRadius, (random.nextDouble() - 0.5) * 4, Math.sin(spawnAngle) * currentRadius);
                        BlockDisplay absorbingBlock = world.spawn(spawnLoc, BlockDisplay.class, bd -> {
                            bd.setBlock(Material.SCULK_VEIN.createBlockData());
                            Transformation t = bd.getTransformation();
                            t.getScale().set(0.1f + (float)random.nextDouble() * 0.2f);
                            bd.setTransformation(t);
                        });
                        absorbingBlocks.add(absorbingBlock);
                    }
                }

                absorbingBlocks.removeIf(bd -> {
                    if (!bd.isValid()) return true;
                    Vector toPlayer = player.getEyeLocation().toVector().subtract(bd.getLocation().toVector()).normalize().multiply(0.4 + progress * 1.2);
                    bd.teleport(bd.getLocation().add(toPlayer));
                    if (bd.getLocation().distanceSquared(player.getEyeLocation()) < 1.5) {
                        world.spawnParticle(Particle.SCULK_SOUL, bd.getLocation(), 1, 0, 0, 0, 0);
                        bd.remove();
                        return true;
                    }
                    return false;
                });

                if (ticks % 12 == 0) {
                    world.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f + (float) progress * 1.5f);
                }
                player.sendTitle("", ChatColor.DARK_AQUA + "" + (int) (progress * 100) + "%", 0, 2, 0);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L));
    }


    private void lockNearbyPlayerEyesight(Player targetPlayer) {
        Location targetLocation = targetPlayer.getEyeLocation();
        for (Entity entity : targetPlayer.getWorld().getNearbyEntities(targetLocation, 30.0, 30.0, 30.0)) {
            if (entity instanceof Player nearbyPlayer && !nearbyPlayer.equals(targetPlayer) &&
                    nearbyPlayer.getGameMode() != GameMode.SPECTATOR) {

                Vector direction = targetLocation.toVector().subtract(nearbyPlayer.getEyeLocation().toVector()).normalize();
                Location newLookLocation = nearbyPlayer.getLocation().setDirection(direction);
                nearbyPlayer.teleport(newLookLocation);
                nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, true, false));
            }
        }
    }


    private List<Vector> createPlayerShellOffsets() {
        List<Vector> offsets = new ArrayList<>();
        float playerHeight = 2.8f;
        float playerWidth = 1.0f;
        // MODIFICATION: Reduced density to decrease entity count and network load
        int density = 14;

        float headRadius = playerWidth / 2 + 0.2f;
        for (int i = 0; i < density; i++) {
            double angle = 2 * Math.PI * i / density;
            for (int j = 0; j < density / 2; j++) {
                double pitch = Math.PI * j / (density / 2.0) - (Math.PI / 2.0);
                double y = Math.sin(pitch) * headRadius;
                double xzRadius = Math.cos(pitch) * headRadius;
                double x = Math.cos(angle) * xzRadius;
                double z = Math.sin(angle) * xzRadius;
                offsets.add(new Vector(x, playerHeight - headRadius + y, z));
            }
        }

        float torsoRadius = playerWidth / 2 + 0.1f;
        for (float y = 0.2f; y < 1.4f; y += 0.3f) {
            for (int i = 0; i < density; i++) {
                double angle = 2 * Math.PI * i / density;
                double x = Math.cos(angle) * torsoRadius;
                double z = Math.sin(angle) * torsoRadius;
                offsets.add(new Vector(x, y, z));
            }
        }

        float armRadius = 0.25f;
        for (float y = 0.4f; y < 1.2f; y += 0.4f) {
            for (int i = 0; i < density / 2; i++) {
                double angle = 2 * Math.PI * i / (density / 2.0);
                double x = torsoRadius + armRadius + (Math.cos(angle) * armRadius);
                offsets.add(new Vector(x, y, Math.sin(angle) * armRadius));
                offsets.add(new Vector(-(x), y, Math.sin(angle) * armRadius));
            }
        }

        return offsets;
    }

    public void startSculkInfestation(Player player) {
        UUID playerUUID = player.getUniqueId();
        data.sculkTasks.put(playerUUID, new BukkitRunnable() {
            int radius = 0;

            @Override
            public void run() {
                if (!data.transformingPlayers.contains(playerUUID) || !player.isOnline() || radius > data.SCULK_SPREAD_RADIUS) {
                    this.cancel();
                    return;
                }
                Location center = player.getLocation();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.abs(x) < radius && Math.abs(z) < radius) continue;
                        Location blockLoc = center.clone().add(x, -1, z);
                        blockLoc = blockLoc.getWorld().getHighestBlockAt(blockLoc).getLocation();
                        if (blockLoc.distanceSquared(center) > data.SCULK_SPREAD_RADIUS * data.SCULK_SPREAD_RADIUS) continue;

                        Material type = blockLoc.getBlock().getType();
                        if (!type.isAir() && type.isSolid() && type != Material.SCULK) {
                            Map<Location, BlockData> playerOriginals = data.originalBlocks.get(player.getUniqueId());
                            if (playerOriginals != null && !playerOriginals.containsKey(blockLoc)) {
                                playerOriginals.put(blockLoc, blockLoc.getBlock().getBlockData());
                                blockLoc.getBlock().setType(Material.SCULK);
                                blockLoc.getWorld().playSound(blockLoc, Sound.BLOCK_SCULK_SPREAD, 1.0f, 1.0f);

                                if (random.nextDouble() < 0.15) {
                                    createVolcano(blockLoc.clone().add(0, 1, 0));
                                }
                            }
                        }
                    }
                }
                radius++;
            }
        }.runTaskTimer(plugin, 0L, 8L));
    }

    private void createVolcano(Location location) {
        final Material originalType = location.getBlock().getType();
        location.getBlock().setType(Material.MAGMA_BLOCK);
        new BukkitRunnable() {
            final int duration = 40 + random.nextInt(40);
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > duration) {
                    if (location.getBlock().getType() == Material.MAGMA_BLOCK) {
                        location.getBlock().setType(originalType);
                    }
                    this.cancel();
                    return;
                }
                if (ticks % 5 == 0) {
                    location.getWorld().spawnParticle(Particle.LAVA, location, 10, 0.5, 0.5, 0.5);
                    location.getWorld().spawnParticle(Particle.SMOKE, location, 5, 0.5, 0.5, 0.5, 0.05);
                    location.getWorld().playSound(location, Sound.BLOCK_LAVA_POP, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void revertSculkBlocks() {
        for (Map<Location, BlockData> playerOriginals : data.originalBlocks.values()) {
            if (playerOriginals == null || playerOriginals.isEmpty()) continue;
            for (Map.Entry<Location, BlockData> entry : playerOriginals.entrySet()) {
                if (entry.getKey().isWorldLoaded()) {
                    entry.getKey().getBlock().setBlockData(entry.getValue());
                }
            }
        }
        data.originalBlocks.clear();
    }
}
