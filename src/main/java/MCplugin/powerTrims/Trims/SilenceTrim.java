package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SilenceTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    private final Map<UUID, Long> wardensEchoCooldowns = new HashMap<>();

    private final long WARDENS_ECHO_COOLDOWN_MS;
    private final double PRIMARY_RADIUS;
    private final int POTION_DURATION_TICKS;
    private final int PEARL_COOLDOWN_TICKS;
    private final double ECHO_RADIUS;
    private final int ECHO_EFFECT_DURATION_TICKS;
    private final int MAX_AFFECTED_ENTITIES;
    private final long PRIMARY_COOLDOWN;
    private static final List<Material> SCULK_MATERIALS = List.of(
            Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST, Material.DEEPSLATE
    );

    public SilenceTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        WARDENS_ECHO_COOLDOWN_MS = configManager.getLong("silence.passive.cooldown");
        PRIMARY_RADIUS = configManager.getDouble("silence.primary.radius");
        POTION_DURATION_TICKS = configManager.getInt("silence.primary.potion_duration_ticks");
        PEARL_COOLDOWN_TICKS = configManager.getInt("silence.primary.pearl_cooldown_ticks");
        ECHO_RADIUS = configManager.getDouble("silence.passive.echo_radius");
        ECHO_EFFECT_DURATION_TICKS = configManager.getInt("silence.passive.effect_duration_ticks");
        MAX_AFFECTED_ENTITIES = configManager.getInt("silence.primary.max_affected_entities");
        PRIMARY_COOLDOWN = configManager.getLong("silence.primary.cooldown");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        abilityManager.registerPrimaryAbility(TrimPattern.SILENCE, this::activateSilencePrimary);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateSilencePrimary(Player player) {
        if (!configManager.isTrimEnabled("silence")) return;
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE) || cooldownManager.isOnCooldown(player, TrimPattern.SILENCE)) return;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        cooldownManager.setCooldown(player, TrimPattern.SILENCE, PRIMARY_COOLDOWN);
        Messaging.sendTrimMessage(player, "Silence", ChatColor.RED, "You have unleashed the Warden's Grasp!");

        List<LivingEntity> targets = new ArrayList<>();
        int affectedCount = 0;
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), PRIMARY_RADIUS)) {
            if (affectedCount >= MAX_AFFECTED_ENTITIES) break;
            if (target.equals(player) || (target instanceof Player p && trustManager.isTrusted(player.getUniqueId(), p.getUniqueId()))) continue;
            targets.add(target);
            affectedCount++;
        }

        playBackTentaclesAnimation(player);

        if (!targets.isEmpty()) {
            playSculkTentacleAnimation(player, targets);
        } else {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 1.5f, 1.8f);
        }
    }

    private void playBackTentaclesAnimation(Player player) {
        final int TENTACLE_COUNT = 5;
        final int SEGMENTS_PER_TENTACLE = 8;
        final double SEGMENT_LENGTH = 0.25;
        final int DURATION_TICKS = 60;
        final List<List<BlockDisplay>> allTentacles = new ArrayList<>();
        // OPTIMIZATION: Increase interpolation to 3 ticks to smooth out the 2-tick timer
        final int INTERPOLATION_TICKS = 3;

        for (int i = 0; i < TENTACLE_COUNT; i++) {
            List<BlockDisplay> currentTentacle = new ArrayList<>();
            for (int j = 0; j < SEGMENTS_PER_TENTACLE; j++) {
                int finalJ = j;
                BlockDisplay segment = player.getWorld().spawn(player.getLocation(), BlockDisplay.class, bd -> {
                    bd.setBlock(Material.SCULK.createBlockData());
                    bd.setInterpolationDuration(INTERPOLATION_TICKS); // Use new value
                    bd.setInterpolationDelay(-1);
                    Transformation t = bd.getTransformation();
                    t.getScale().set(0.2f - (finalJ * 0.01f));
                    bd.setTransformation(t);
                });
                currentTentacle.add(segment);
            }
            allTentacles.add(currentTentacle);
        }

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                // OPTIMIZATION: Stop task if ticks * period > duration
                if ((ticks * 2) > DURATION_TICKS || !player.isValid()) {
                    this.cancel();
                    allTentacles.forEach(tentacle -> tentacle.forEach(BlockDisplay::remove));
                    return;
                }
                ticks++;

                Vector backDir = player.getLocation().getDirection().clone().multiply(-1);
                Vector sideDir = backDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                Location anchor = player.getLocation().add(backDir.multiply(0.4)).add(0, 1.2, 0);

                for (int i = 0; i < allTentacles.size(); i++) {
                    double horizontalOffset = (i - (TENTACLE_COUNT - 1) / 2.0) * 0.3;
                    Location currentAnchor = anchor.clone().add(sideDir.clone().multiply(horizontalOffset));
                    Vector previousSegmentPos = currentAnchor.toVector();
                    List<BlockDisplay> currentTentacle = allTentacles.get(i);

                    for (int j = 0; j < currentTentacle.size(); j++) {
                        BlockDisplay segment = currentTentacle.get(j);
                        if (!segment.isValid()) continue;

                        Vector dir = player.getLocation().getDirection().clone().multiply(-1);
                        double wave = Math.sin(ticks * 2 * 0.3 + i * 1.5 + j * 0.5) * 0.6; // Adjust wave speed for new timer
                        dir.add(sideDir.clone().multiply(wave * 0.5));
                        dir.setY(dir.getY() + Math.cos(ticks * 2 * 0.2 + i) * 0.4 - (j * 0.05));

                        Vector newPos = previousSegmentPos.clone().add(dir.normalize().multiply(SEGMENT_LENGTH));
                        segment.teleport(newPos.toLocation(player.getWorld()));
                        previousSegmentPos = newPos;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // OPTIMIZATION: Changed from 1L to 2L
    }


    private void playSculkTentacleAnimation(Player player, List<LivingEntity> targets) {
        final int extendTicks = 20;
        final int graspTicks = 20;
        final Map<LivingEntity, List<BlockDisplay>> tendrils = new HashMap<>();
        final Map<LivingEntity, BlockDisplay> chargeSpheres = new HashMap<>();
        // OPTIMIZATION: Increase interpolation to 4 ticks to smooth out the 2-tick timer
        final int INTERPOLATION_TICKS = 4;

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 2.0f, 1.2f);

        for (LivingEntity target : targets) {
            BlockDisplay charge = player.getWorld().spawn(player.getEyeLocation(), BlockDisplay.class, bd -> {
                bd.setBlock(Material.SCULK_SHRIEKER.createBlockData());
                bd.setBrightness(new Display.Brightness(15, 15));
                bd.setInterpolationDuration(INTERPOLATION_TICKS); // Use new value
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(0.4f);
                bd.setTransformation(t);
            });
            chargeSpheres.put(target, charge);

            double distance = player.getLocation().distance(target.getLocation());
            int tendrilLinks = (int) (distance * 2.5);
            List<BlockDisplay> links = new ArrayList<>();
            for (int i = 0; i < tendrilLinks; i++) {
                BlockDisplay link = player.getWorld().spawn(player.getEyeLocation(), BlockDisplay.class, bd -> {
                    bd.setBlock(Material.SCULK.createBlockData());
                    bd.setInterpolationDuration(INTERPOLATION_TICKS); // Use new value
                    bd.setInterpolationDelay(-1);
                    Transformation t = bd.getTransformation();
                    t.getScale().set(0.25f);
                    bd.setTransformation(t);
                });
                links.add(link);
            }
            tendrils.put(target, links);
        }

        new BukkitRunnable() {
            private int ticks = 0; // This will now increment every 2 server ticks

            @Override
            public void run() {
                // OPTIMIZATION: Stop task if ticks * period > duration
                if ((ticks * 2) >= (extendTicks + graspTicks) || !player.isValid()) {
                    this.cancel();
                    return;
                }
                ticks++;

                Location playerAnchor = player.getEyeLocation().add(0, -0.5, 0);

                for (Map.Entry<LivingEntity, List<BlockDisplay>> entry : tendrils.entrySet()) {
                    LivingEntity target = entry.getKey();
                    if (!target.isValid()) continue;

                    // Adjust progress calculation for the 2-tick timer
                    double extendProgress = Math.min(1.0, (double) (ticks * 2) / extendTicks);
                    Location targetAnchor = target.getLocation().add(0, target.getHeight() / 2, 0);
                    Vector toTarget = targetAnchor.toVector().subtract(playerAnchor.toVector());

                    List<BlockDisplay> links = entry.getValue();
                    for (int i = 0; i < links.size(); i++) {
                        BlockDisplay link = links.get(i);
                        if (!link.isValid()) continue;

                        double progress = (double) i / (links.size() - 1);
                        Location linkPos = playerAnchor.clone().add(toTarget.clone().multiply(progress * extendProgress));
                        linkPos.add(0, Math.sin(ticks * 2 * 0.5 + i * 0.5) * 0.2, 0); // Adjust wave speed
                        link.teleport(linkPos);
                    }

                    BlockDisplay charge = chargeSpheres.get(target);
                    if (charge != null && charge.isValid()) {
                        double chargeProgress = (double) (ticks * 2) / (extendTicks + graspTicks); // Adjust progress
                        Location chargePos = playerAnchor.clone().add(toTarget.clone().multiply(chargeProgress));
                        charge.teleport(chargePos);
                    }
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                for (LivingEntity target : targets) {
                    if (target.isValid()) {
                        playSculkExplosion(target.getLocation().add(0, target.getHeight() / 2, 0));
                        applyPrimaryEffects(target);
                        if (target instanceof Player p) {
                            p.setCooldown(Material.ENDER_PEARL, PEARL_COOLDOWN_TICKS);
                            sendMessages(p);
                        }
                    }
                }
                tendrils.values().forEach(list -> list.forEach(BlockDisplay::remove));
                chargeSpheres.values().forEach(BlockDisplay::remove);
            }
        }.runTaskTimer(plugin, 0L, 2L); // OPTIMIZATION: Changed from 1L to 2L
    }



    private void playSculkExplosion(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.2f);
        int particleCount = 40;
        int animationTicks = 20;
        // OPTIMIZATION: Collect shards to remove them with one task
        final List<BlockDisplay> shards = new ArrayList<>(particleCount);

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            BlockDisplay shard = location.getWorld().spawn(location, BlockDisplay.class, bd -> {
                bd.setBlock(SCULK_MATERIALS.get(r.nextInt(SCULK_MATERIALS.size())).createBlockData());
                bd.setInterpolationDuration(animationTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(r.nextFloat() * 0.7f + 0.4f);
                t.getLeftRotation().set(new AxisAngle4f(r.nextFloat() * 360, r.nextFloat(), r.nextFloat(), r.nextFloat()));
                bd.setTransformation(t);
            });
            shards.add(shard); // Add to list

            Vector randomVec = new Vector(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5);
            Location finalPos = location.clone().add(randomVec.normalize().multiply(3.0));
            shard.teleport(finalPos);

            Transformation finalTransform = shard.getTransformation();
            finalTransform.getScale().set(0f);
            shard.setTransformation(finalTransform);

            // OPTIMIZATION: Removed individual runnable creation
        }

        // OPTIMIZATION: Create one runnable to remove all shards
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockDisplay shard : shards) {
                    if (shard.isValid()) {
                        shard.remove();
                    }
                }
            }
        }.runTaskLater(plugin, animationTicks + 5); // Add 5 ticks buffer
    }


    private void playEchoingVengeanceAnimation(Player player) {
        Location center = player.getLocation();
        int particleCount = 70;
        int animationTicks = 30;
        // OPTIMIZATION: Collect shards to remove them with one task
        final List<BlockDisplay> shards = new ArrayList<>(particleCount);

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            BlockDisplay shard = player.getWorld().spawn(center, BlockDisplay.class, bd -> {
                bd.setBlock(SCULK_MATERIALS.get(r.nextInt(SCULK_MATERIALS.size())).createBlockData());
                bd.setInterpolationDuration(animationTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(r.nextFloat() * 0.8f + 0.4f);
                t.getLeftRotation().set(new AxisAngle4f(r.nextFloat() * 360, r.nextFloat(), r.nextFloat(), r.nextFloat()));
                bd.setTransformation(t);
            });
            shards.add(shard); // Add to list

            Location finalPos = center.clone().add(Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(ECHO_RADIUS));
            shard.teleport(finalPos);

            Transformation finalTransform = shard.getTransformation();
            finalTransform.getScale().set(0f);
            shard.setTransformation(finalTransform);

            // OPTIMIZATION: Removed individual runnable creation
        }

        // OPTIMIZATION: Create one runnable to remove all shards
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockDisplay shard : shards) {
                    if (shard.isValid()) {
                        shard.remove();
                    }
                }
            }
        }.runTaskLater(plugin, animationTicks + 5); // Add 5 ticks buffer

        Location heartPos = player.getEyeLocation().add(0, 1.5, 0);
        BlockDisplay shrieker = player.getWorld().spawn(heartPos, BlockDisplay.class, bd -> {
            bd.setBlock(Material.SCULK_SHRIEKER.createBlockData());
            bd.setInterpolationDuration(10);
            bd.setInterpolationDelay(-1);
            Transformation t = bd.getTransformation();
            t.getScale().set(0f);
            bd.setTransformation(t);
        });

        new BukkitRunnable() {
            @Override
            public void run() {
                Transformation t = shrieker.getTransformation();
                t.getScale().set(0.8f);
                shrieker.setTransformation(t);
            }
        }.runTaskLater(plugin, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!shrieker.isValid()) return;
                Transformation t = shrieker.getTransformation();
                t.getScale().set(0f);
                shrieker.setTransformation(t);
                new BukkitRunnable() { @Override public void run() { shrieker.remove(); }}.runTaskLater(plugin, 11L);
            }
        }.runTaskLater(plugin, ECHO_EFFECT_DURATION_TICKS);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double healthAfterDamage = player.getHealth() - event.getFinalDamage();
        if (healthAfterDamage > 8.0) return;

        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE)) return;

        if (wardensEchoCooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        activateWardensEcho(player);
    }

    private void activateWardensEcho(Player player) {
        Location playerLocation = player.getLocation();
        player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
        playEchoingVengeanceAnimation(player);

        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(playerLocation, ECHO_RADIUS)) {
            if (target.equals(player)) continue;

            if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                continue;
            }
            applyEchoEffects(player, target);
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ECHO_EFFECT_DURATION_TICKS, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ECHO_EFFECT_DURATION_TICKS, 1));
        Messaging.sendTrimMessage(player, "Silence", ChatColor.RED, "Your armor has unleashed " + ChatColor.BOLD + "Warden's Echo!");

        wardensEchoCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + WARDENS_ECHO_COOLDOWN_MS);
    }

    private void applyPrimaryEffects(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, POTION_DURATION_TICKS, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, POTION_DURATION_TICKS, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, POTION_DURATION_TICKS, 1));
    }

    private void applyEchoEffects(Player player, LivingEntity target) {
        Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.2);
        knockback.setY(0.5);
        target.setVelocity(knockback);

        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ECHO_EFFECT_DURATION_TICKS, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ECHO_EFFECT_DURATION_TICKS, 1));

        if (target instanceof Player playerTarget) {
            Messaging.sendTrimMessage(playerTarget, "Silence", ChatColor.RED, "You were hit by " + ChatColor.BOLD + "Warden's Echo!");
        }
    }

    private void sendMessages(Player targetPlayer) {
        Messaging.sendTrimMessage(targetPlayer, "Silence", ChatColor.RED, "You have been hit with the " + ChatColor.BOLD + "Warden's Roar!");
        Messaging.sendTrimMessage(targetPlayer, "Silence", ChatColor.RED, "Your Ender Pearl is on cooldown!");
    }
}