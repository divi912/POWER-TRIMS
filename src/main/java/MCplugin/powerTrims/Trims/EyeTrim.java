package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EyeTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final double TRUE_SIGHT_RADIUS;
    private final int TRUE_SIGHT_DURATION_TICKS;
    private final long TRUE_SIGHT_COOLDOWN;
    private final long TASK_INTERVAL_TICKS;
    private final double TRUE_SIGHT_VERTICAL_RADIUS;
    private final Map<UUID, BukkitRunnable> activeTrueSightTasks = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeAnimationTasks = new HashMap<>();
    private final Map<UUID, List<Entity>> activeEyeEffects = new HashMap<>();

    public EyeTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        TRUE_SIGHT_RADIUS = configManager.getDouble("eye.primary.true_sight_radius");
        TRUE_SIGHT_DURATION_TICKS = configManager.getInt("eye.primary.true_sight_duration_ticks");
        TRUE_SIGHT_COOLDOWN = configManager.getLong("trim.eye.primary.cooldown");
        TASK_INTERVAL_TICKS = configManager.getLong("eye.primary.task_interval_ticks");
        TRUE_SIGHT_VERTICAL_RADIUS = configManager.getDouble("eye.primary.true_sight_vertical_radius");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        abilityManager.registerPrimaryAbility(TrimPattern.EYE, this::activateEyePrimary);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateEyePrimary(Player player) {
        if (!configManager.isTrimEnabled("eye")) {
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.EYE)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.EYE)) return;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        activeTrueSightTasks.computeIfPresent(player.getUniqueId(), (uuid, task) -> {
            task.cancel();
            return null;
        });

        startEyeEffect(player);
        BukkitRunnable trueSightTask = getBukkitRunnable(player);

        activeTrueSightTasks.put(player.getUniqueId(), trueSightTask);
        trueSightTask.runTaskTimer(plugin, 0L, TASK_INTERVAL_TICKS);

        cooldownManager.setCooldown(player, TrimPattern.EYE, TRUE_SIGHT_COOLDOWN);
        Messaging.sendTrimMessage(player, "Eye", ChatColor.AQUA, "you used eye ability!");
    }

    private @NotNull BukkitRunnable getBukkitRunnable(Player player) {
        final Set<UUID> affectedEntities = new HashSet<>();

        return new BukkitRunnable() {
            private int ticksRun = 0;

            @Override
            public void run() {
                if (ticksRun >= TRUE_SIGHT_DURATION_TICKS || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                for (Entity entity : player.getNearbyEntities(TRUE_SIGHT_RADIUS, TRUE_SIGHT_VERTICAL_RADIUS, TRUE_SIGHT_RADIUS)) {
                    if (entity instanceof LivingEntity target && !target.equals(player)) {
                        if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                            continue;
                        }
                        if (affectedEntities.add(target.getUniqueId())) {
                            applyDebuffs(target);
                        }
                    }
                }
                if (ticksRun % (TASK_INTERVAL_TICKS * 5) == 0) {
                    startEyeEffect(player);
                }

                ticksRun += (int) TASK_INTERVAL_TICKS;
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                activeTrueSightTasks.remove(player.getUniqueId());
            }
        };
    }

    private void applyDebuffs(LivingEntity target) {
        target.removePotionEffect(PotionEffectType.INVISIBILITY);

        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 1200, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 1, false, false));
    }

    private void startEyeEffect(Player player) {
        stopEyeEffect(player);

        World world = player.getWorld();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 2.0f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);

        new BukkitRunnable() {
            private int ticks = 0;
            private final int duration = 20;

            @Override
            public void run() {
                if (ticks++ >= duration || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                double progress = (double) ticks / duration;
                double currentRadius = TRUE_SIGHT_RADIUS * progress;
                Location center = player.getLocation();

                for (int i = 0; i < 360; i += 10) {
                    double angle = Math.toRadians(i);
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    Location particleLoc = center.clone().add(x, 0.1, z);
                    world.spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        final List<Entity> effectEntities = new ArrayList<>();
        final int INTERPOLATION_DURATION = 5;

        ItemDisplay eye = world.spawn(player.getEyeLocation(), ItemDisplay.class, eyeDisplay -> {
            eyeDisplay.setItemStack(new ItemStack(Material.ENDER_EYE));
            eyeDisplay.setBillboard(Display.Billboard.CENTER);
            eyeDisplay.setInterpolationDelay(-1);
            eyeDisplay.setInterpolationDuration(INTERPOLATION_DURATION);
            Transformation t = eyeDisplay.getTransformation();
            t.getScale().set(0f);
            eyeDisplay.setTransformation(t);
        });
        effectEntities.add(eye);

        activeEyeEffects.put(player.getUniqueId(), effectEntities);

        BukkitRunnable animationTask = new BukkitRunnable() {
            private int ticks = 0;
            private final int vanishTick = 60;

            @Override
            public void run() {
                if (!player.isOnline() || !activeEyeEffects.containsKey(player.getUniqueId())) {
                    this.cancel();
                    return;
                }

                if (ticks >= vanishTick) {
                    stopEyeEffect(player);
                    this.cancel();
                    return;
                }

                Location eyeTargetPos = player.getEyeLocation();
                Vector direction = eyeTargetPos.getDirection().setY(0).normalize();
                eyeTargetPos.add(direction.multiply(-0.7)).add(0, 0.4, 0);

                if (ticks == 0) {
                    eye.teleport(eyeTargetPos);
                    Transformation t = eye.getTransformation();
                    t.getScale().set(0.6f);
                    eye.setTransformation(t);
                } else {
                    eye.teleport(eyeTargetPos);
                }
                ticks++;
            }
        };

        animationTask.runTaskTimer(plugin, 0L, 1L);
        activeAnimationTasks.put(player.getUniqueId(), animationTask);
    }

    private void stopEyeEffect(Player player) {
        UUID id = player.getUniqueId();

        if (activeAnimationTasks.containsKey(id)) {
            activeAnimationTasks.get(id).cancel();
            activeAnimationTasks.remove(id);
        }

        if (activeEyeEffects.containsKey(id)) {
            List<Entity> entities = activeEyeEffects.remove(id);
            if (entities != null && !entities.isEmpty()) {
                Entity eyeEntity = entities.get(0);
                if (eyeEntity instanceof ItemDisplay eye && eye.isValid()) {
                    Transformation t = eye.getTransformation();
                    t.getScale().set(0f);
                    eye.setTransformation(t);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (eye.isValid()) eye.remove();
                        }
                    }.runTaskLater(plugin, eye.getInterpolationDuration() + 1);
                }
            }
        }
    }

    public void cleanup() {
        for (BukkitRunnable task : activeTrueSightTasks.values()) {
            task.cancel();
        }
        activeTrueSightTasks.clear();

        for (BukkitRunnable task : activeAnimationTasks.values()) {
            task.cancel();
        }
        activeAnimationTasks.clear();

        for (List<Entity> entities : activeEyeEffects.values()) {
            for (Entity entity : entities) {
                entity.remove();
            }
        }
        activeEyeEffects.clear();
    }
}