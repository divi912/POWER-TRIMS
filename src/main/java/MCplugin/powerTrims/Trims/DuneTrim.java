package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DuneTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final long TORNADO_COOLDOWN;
    private final int TORNADO_DURATION;
    private final double TORNADO_HEIGHT;
    private final double PULL_STRENGTH;
    private final double LIFT_STRENGTH;
    private final double DAMAGE_PER_SECOND;

    private static final List<Material> SAND_MATERIALS = List.of(
            Material.SAND, Material.SANDSTONE, Material.RED_SAND, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE
    );

    private final List<BukkitTask> activeTornadoTasks = new ArrayList<>();

    private record TornadoParticle(BlockDisplay display, double height, double radius, double speed, double startAngle) {}

    public DuneTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        TORNADO_COOLDOWN = configManager.getLong("dune.primary.cooldown");
        TORNADO_DURATION = configManager.getInt("dune.primary.duration");
        TORNADO_HEIGHT = configManager.getDouble("dune.primary.height");
        PULL_STRENGTH = configManager.getDouble("dune.primary.pull_strength");
        LIFT_STRENGTH = configManager.getDouble("dune.primary.lift_strength");
        DAMAGE_PER_SECOND = configManager.getDouble("dune.primary.damage_per_second");

        abilityManager.registerPrimaryAbility(TrimPattern.DUNE, this::dunePrimary);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void dunePrimary(Player player) {
        if (!configManager.isTrimEnabled("dune")) return;
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.DUNE)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.DUNE)) return;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You must be looking at the ground to summon the tornado.");
            return;
        }

        Block targetBlock = player.getTargetBlockExact(30, FluidCollisionMode.NEVER);
        if (targetBlock == null) {
            Messaging.sendError(player, "You must be looking at the ground to summon the tornado.");
            return;
        }

        cooldownManager.setCooldown(player, TrimPattern.DUNE, TORNADO_COOLDOWN);
        Messaging.sendTrimMessage(player, "Dune", ChatColor.GOLD, "You have used dune ability!");
        startTornadoTask(player, targetBlock.getLocation().add(0.5, 1.0, 0.5));
    }

    private void startTornadoTask(Player owner, Location initialLocation) {
        final World world = initialLocation.getWorld();
        if (world == null) return;

        final List<TornadoParticle> particles = new ArrayList<>();
        final int particleCount = 150;

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            BlockDisplay display = world.spawn(initialLocation, BlockDisplay.class, bd -> {
                bd.setBlock(SAND_MATERIALS.get(r.nextInt(SAND_MATERIALS.size())).createBlockData());
                bd.setInterpolationDuration(5);
                bd.setInterpolationDelay(-1);
            });

            double height = r.nextDouble(TORNADO_HEIGHT);
            double radius = height * 0.4 + 1.5;
            if (radius > 5.0) radius = 5.0;

            particles.add(new TornadoParticle(
                    display,
                    height,
                    radius,
                    r.nextDouble(20, 40) * (r.nextBoolean() ? 1 : -1),
                    r.nextDouble(360)
            ));
        }

        BukkitRunnable tornadoTask = new BukkitRunnable() {
            private int ticksElapsed = 0;
            private final Location tornadoLocation = initialLocation.clone();
            private final Vector moveDirection = owner.getEyeLocation().getDirection().setY(0).normalize().multiply(0.5);

            @Override
            public void run() {
                if (ticksElapsed >= TORNADO_DURATION || !owner.isValid()) {
                    this.cancel();
                    return;
                }
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Vector wobble = new Vector(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5).multiply(0.4);
                tornadoLocation.add(moveDirection.clone().add(wobble));
                tornadoLocation.setY(world.getHighestBlockYAt(tornadoLocation) + 1.0);
                for (Entity entity : world.getNearbyEntities(tornadoLocation, 8, TORNADO_HEIGHT, 8)) {
                    if (entity instanceof LivingEntity target && !entity.equals(owner) && !(entity instanceof Player p && trustManager.isTrusted(owner.getUniqueId(), p.getUniqueId()))) {
                        Vector pull = tornadoLocation.toVector().subtract(target.getLocation().toVector()).normalize().multiply(PULL_STRENGTH);
                        Vector lift = new Vector(0, LIFT_STRENGTH, 0);
                        target.setVelocity(pull.add(lift));
                        if (ticksElapsed % 20 == 0) target.damage(DAMAGE_PER_SECOND, owner);
                    }
                }

                for (TornadoParticle particle : particles) {
                    if (!particle.display.isValid()) continue;
                    double angle = Math.toRadians(particle.startAngle + (ticksElapsed * particle.speed));

                    Location particleLoc = tornadoLocation.clone().add(
                            Math.cos(angle) * particle.radius,
                            particle.height,
                            Math.sin(angle) * particle.radius
                    );

                    Transformation t = particle.display.getTransformation();
                    t.getScale().set(r.nextFloat() * 0.8f + 0.2f);

                    particle.display.teleport(particleLoc);
                    particle.display.setTransformation(t);
                }

                world.playSound(tornadoLocation, Sound.ENTITY_BREEZE_IDLE_AIR, 0.8f, (float)(1.5 + r.nextDouble() * 0.2));
                ticksElapsed += 4;
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                world.playSound(tornadoLocation, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.0f);
                for (TornadoParticle particle : particles) {
                    if (particle.display.isValid()) particle.display.remove();
                }
                activeTornadoTasks.remove(this);
            }
        };
        activeTornadoTasks.add(tornadoTask.runTaskTimer(plugin, 0L, 4L));
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void cleanup() {
        for (BukkitTask task : activeTornadoTasks) {
            task.cancel();
        }
        activeTornadoTasks.clear();
    }
}
