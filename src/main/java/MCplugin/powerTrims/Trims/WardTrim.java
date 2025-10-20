package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.*;

public class WardTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    private final int BARRIER_DURATION;
    private final int ABSORPTION_LEVEL;
    private final int RESISTANCE_BOOST_LEVEL;
    private final long WARD_COOLDOWN;

    private record RibTrimPart(BlockDisplay display, Vector relativePosition, Quaternionf initialRotation) {}
    private final Map<UUID, BukkitRunnable> activeRibTrimTasks = new HashMap<>();
    private final Map<UUID, List<RibTrimPart>> activeRibCages = new HashMap<>();

    public WardTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        BARRIER_DURATION = configManager.getInt("ward.primary.barrier_duration");
        ABSORPTION_LEVEL = configManager.getInt("ward.primary.absorption_level");
        RESISTANCE_BOOST_LEVEL = configManager.getInt("ward.primary.resistance_boost_level");
        WARD_COOLDOWN = configManager.getLong("ward.primary.cooldown");

        abilityManager.registerPrimaryAbility(TrimPattern.WARD, this::wardPrimary);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void wardPrimary(Player player) {
        if (!configManager.isTrimEnabled("ward")) return;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WARD) || cooldownManager.isOnCooldown(player, TrimPattern.WARD)) return;

        cooldownManager.setCooldown(player, TrimPattern.WARD, WARD_COOLDOWN);
        Messaging.sendTrimMessage(player, "Ward", ChatColor.YELLOW, "You have summoned the Bone RibTrim!");

        playRibAnimation(player, () -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, BARRIER_DURATION, ABSORPTION_LEVEL, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, BARRIER_DURATION, RESISTANCE_BOOST_LEVEL, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, BARRIER_DURATION, 0, true, true, true));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
        });
    }

    private void playRibAnimation(Player player, Runnable onFormationComplete) {
        stopRibTrimAnimation(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VEX_CHARGE, 1.5f, 0.8f);

        List<RibTrimPart> ribCageParts = new ArrayList<>();
        activeRibCages.put(player.getUniqueId(), ribCageParts);

        BukkitRunnable RibTrimTask = new BukkitRunnable() {
            private int localTicks = 0;
            private boolean spineSpawned = false;

            @Override
            public void run() {
                if (!player.isValid() || !activeRibCages.containsKey(player.getUniqueId())) {
                    this.cancel();
                    return;
                }

                if (!spineSpawned) {
                    spawnSpine(player, ribCageParts);
                    spineSpawned = true;
                }

                int totalRibPairs = 3;
                int formationSpeed = 5;
                if (localTicks < totalRibPairs * formationSpeed && localTicks % formationSpeed == 0) {
                    int ribIndex = localTicks / formationSpeed;
                    spawnRibPair(player, ribIndex, ribCageParts);
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BONE_BLOCK_PLACE, 1.0f, 1.8f - (ribIndex * 0.2f));
                    if (ribIndex == totalRibPairs - 1) {
                        onFormationComplete.run();
                    }
                }

                Location playerLoc = player.getLocation();
                Quaternionf playerRotation = new Quaternionf().rotateY((float) Math.toRadians(-playerLoc.getYaw() + 180));
                for (RibTrimPart part : ribCageParts) {
                    if (!part.display.isValid()) continue;

                    Vector rotatedOffset = part.relativePosition.clone().rotateAroundY(Math.toRadians(playerLoc.getYaw()));
                    Location targetPos = playerLoc.clone().add(rotatedOffset);
                    Quaternionf newRotation = new Quaternionf(playerRotation).mul(part.initialRotation);

                    part.display.setInterpolationDuration(3);
                    part.display.teleport(targetPos);
                    Transformation t = part.display.getTransformation();
                    t.getLeftRotation().set(newRotation);
                    part.display.setTransformation(t);
                }
                localTicks++;
            }
        };
        RibTrimTask.runTaskTimer(plugin, 15L, 2L);
        activeRibTrimTasks.put(player.getUniqueId(), RibTrimTask);

        new BukkitRunnable() {
            @Override
            public void run() {
                stopRibTrimAnimation(player.getUniqueId());
            }
        }.runTaskLater(plugin, BARRIER_DURATION);
    }

    private void spawnSpine(Player player, List<RibTrimPart> ribCageParts) {
        for (int i = 0; i < 4; i++) {
            double y = 0.8 + (i * 0.25);
            Vector relPos = new Vector(0, y, -0.25);
            Quaternionf relRot = new Quaternionf();

            Location spawnLoc = player.getLocation().clone().add(relPos.clone().rotateAroundY(Math.toRadians(player.getLocation().getYaw())));
            BlockDisplay spinePart = player.getWorld().spawn(spawnLoc, BlockDisplay.class, bd -> {
                bd.setBlock(Material.BONE_BLOCK.createBlockData());
                bd.setInterpolationDuration(10);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(0f);
                bd.setTransformation(t);
            });
            ribCageParts.add(new RibTrimPart(spinePart, relPos, relRot));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (spinePart.isValid()) {
                        Transformation t = spinePart.getTransformation();
                        t.getScale().set(0.25f, 0.25f, 0.25f);
                        spinePart.setTransformation(t);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    private void spawnRibPair(Player player, int ribIndex, List<RibTrimPart> ribCageParts) {
        int segmentsPerRib = 10;

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < segmentsPerRib; i++) {
                Map.Entry<Vector, Quaternionf> transformData = getRibSegment(ribIndex, i, segmentsPerRib, side);

                Location playerLoc = player.getLocation();
                Vector initialRotatedOffset = transformData.getKey().clone().rotateAroundY(Math.toRadians(playerLoc.getYaw()));
                Location spawnLoc = playerLoc.clone().add(initialRotatedOffset);
                Quaternionf playerRotation = new Quaternionf().rotateY((float) Math.toRadians(-playerLoc.getYaw() + 180));
                Quaternionf initialBlockRotation = new Quaternionf(playerRotation).mul(transformData.getValue());

                BlockDisplay ribSegment = player.getWorld().spawn(spawnLoc, BlockDisplay.class, bd -> {
                    bd.setBlock(Material.BONE_BLOCK.createBlockData());
                    bd.setInterpolationDuration(10);
                    bd.setInterpolationDelay(-1);
                    Transformation t = bd.getTransformation();
                    t.getScale().set(0f);
                    t.getLeftRotation().set(initialBlockRotation);
                    bd.setTransformation(t);
                });

                ribCageParts.add(new RibTrimPart(ribSegment, transformData.getKey(), transformData.getValue()));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (ribSegment.isValid()) {
                            Transformation t = ribSegment.getTransformation();
                            t.getScale().set(0.18f, 0.18f, 0.45f);
                            ribSegment.setTransformation(t);
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }
        }
    }

    private Map.Entry<Vector, Quaternionf> getRibSegment(int ribIndex, int segmentIndex, int totalSegments, int side) {
        double yOffset = 1.2 - (ribIndex * 0.3);
        double curveProgress = (double) segmentIndex / (totalSegments - 1);

        double maxAngle = Math.toRadians(80);
        double curveAngle = maxAngle * curveProgress;

        double radius = 0.7;
        double spineZPosition = -0.25;

        double sideOffset = Math.sin(curveAngle) * radius;
        double forwardOffset = -Math.cos(curveAngle) * radius;

        forwardOffset += (radius + spineZPosition);

        Vector relativePosition = new Vector(sideOffset * side, yOffset, forwardOffset);

        Quaternionf relativeRotation = new Quaternionf()
                .rotateY((float) (side * curveAngle))
                .rotateZ((float) Math.toRadians(side * -15));

        return new AbstractMap.SimpleEntry<>(relativePosition, relativeRotation);
    }

    private void stopRibTrimAnimation(UUID playerId) {
        if (activeRibTrimTasks.containsKey(playerId)) {
            activeRibTrimTasks.get(playerId).cancel();
            activeRibTrimTasks.remove(playerId);
        }
        if (activeRibCages.containsKey(playerId)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SKELETON_DEATH, 1.0f, 1.2f);

            for (RibTrimPart part : activeRibCages.get(playerId)) {
                BlockDisplay display = part.display;
                if (display.isValid()) {
                    display.setInterpolationDuration(15);
                    Transformation t = display.getTransformation();
                    t.getScale().set(0f);
                    display.setTransformation(t);
                    new BukkitRunnable() { @Override public void run() { display.remove(); }}.runTaskLater(plugin, 16L);
                }
            }
            activeRibCages.remove(playerId);
        }
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (!configManager.isTrimEnabled("ward")) return;
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }
}
