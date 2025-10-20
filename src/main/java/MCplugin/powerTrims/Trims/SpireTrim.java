package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

public class SpireTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final AbilityManager abilityManager;
    private final Set<UUID> markedTargets;
    private final Set<UUID> dashingPlayers;
    private final ConfigManager configManager;

    private final double DASH_DISTANCE;
    private final double DASH_SPEED;
    private final double KNOCKBACK_STRENGTH;
    private final int SLOW_DURATION;
    private final int VULNERABLE_DURATION;
    private final double DAMAGE_AMPLIFICATION;
    private final long ABILITY_COOLDOWN;

    private static final List<Material> TRAIL_MATERIALS = List.of(Material.ICE, Material.PACKED_ICE, Material.PRISMARINE_BRICKS);
    private static final List<Material> SHATTER_MATERIALS = List.of(Material.GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.CYAN_STAINED_GLASS);

    public SpireTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;
        this.markedTargets = new HashSet<>();
        this.dashingPlayers = new HashSet<>();

        DASH_DISTANCE = configManager.getDouble("spire.primary.dash_distance");
        DASH_SPEED = configManager.getDouble("spire.primary.dash_speed");
        KNOCKBACK_STRENGTH = configManager.getDouble("spire.primary.knockback_strength");
        SLOW_DURATION = configManager.getInt("spire.primary.slow_duration");
        VULNERABLE_DURATION = configManager.getInt("spire.primary.vulnerable_duration");
        DAMAGE_AMPLIFICATION = configManager.getDouble("spire.primary.damage_amplification");
        ABILITY_COOLDOWN = configManager.getLong("spire.primary.cooldown");

        abilityManager.registerPrimaryAbility(TrimPattern.SPIRE, this::spirePrimary);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void spirePrimary(Player player) {
        if (!configManager.isTrimEnabled("spire")) return;
        if (dashingPlayers.contains(player.getUniqueId())) return;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SPIRE) || cooldownManager.isOnCooldown(player, TrimPattern.SPIRE)) return;

        Location startLoc = player.getLocation();
        Vector direction = player.getLocation().getDirection().normalize();

        player.getWorld().playSound(startLoc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.8f);

        UUID playerId = player.getUniqueId();
        dashingPlayers.add(playerId);
        player.setInvulnerable(true);
        player.setVelocity(direction.multiply(DASH_SPEED));

        final List<BlockDisplay> vortexBlocks = new ArrayList<>();
        final int VORTEX_ARMS = 3;
        final int BLOCKS_PER_ARM = 4;
        final double VORTEX_RADIUS = 1.2;
        final ThreadLocalRandom r = ThreadLocalRandom.current();

        for (int i = 0; i < VORTEX_ARMS * BLOCKS_PER_ARM; i++) {
            BlockDisplay vortexBlock = player.getWorld().spawn(player.getLocation(), BlockDisplay.class, bd -> {
                bd.setBlock(TRAIL_MATERIALS.get(r.nextInt(TRAIL_MATERIALS.size())).createBlockData());
                Transformation t = bd.getTransformation();
                t.getScale().set(0.4f);
                bd.setTransformation(t);
            });
            vortexBlocks.add(vortexBlock);
        }

        new BukkitRunnable() {
            private double distanceTraveled = 0;
            private int animationTick = 0;
            private Location lastLoc = startLoc.clone();
            private final Set<Entity> hitEntities = new HashSet<>();

            @Override
            public void run() {
                if (!player.isOnline() || distanceTraveled >= DASH_DISTANCE) {
                    endDash();
                    return;
                }

                Location currentLoc = player.getLocation();
                distanceTraveled += lastLoc.distance(currentLoc);

                // --- Flow/Vortex Animation ---
                Location center = player.getLocation().add(0, 1, 0);
                double rotation = Math.toRadians(animationTick * 25);

                for (int i = 0; i < vortexBlocks.size(); i++) {
                    BlockDisplay block = vortexBlocks.get(i);
                    if (!block.isValid()) continue;

                    double armOffset = (double) (i % VORTEX_ARMS) / VORTEX_ARMS * (2 * Math.PI);
                    double yOffset = ((double) (i / VORTEX_ARMS) / BLOCKS_PER_ARM - 0.5) * 2.0;

                    double currentAngle = rotation + armOffset + (distanceTraveled * 2);
                    double x = Math.cos(currentAngle) * VORTEX_RADIUS;
                    double z = Math.sin(currentAngle) * VORTEX_RADIUS;

                    block.teleport(center.clone().add(x, yOffset, z));
                }
                animationTick++;
                // --- End Animation ---


                for (Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity && !entity.equals(player) && hitEntities.add(entity)) {
                        handleEntityCollision((LivingEntity) entity, direction, player);
                    }
                }

                lastLoc = currentLoc;
            }

            private void endDash() {
                dashingPlayers.remove(playerId);
                player.setInvulnerable(false);
                player.setVelocity(player.getVelocity().multiply(0.1));
                cooldownManager.setCooldown(player, TrimPattern.SPIRE, ABILITY_COOLDOWN);
                vortexBlocks.forEach(Entity::remove);
                this.cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void handleEntityCollision(LivingEntity target, Vector dashDirection, Player damager) {
        if (target instanceof Player p && trustManager.isTrusted(damager.getUniqueId(), p.getUniqueId())) return;

        Vector knockbackVec = dashDirection.clone().multiply(KNOCKBACK_STRENGTH).setY(0.4);
        target.setVelocity(knockbackVec);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_DURATION, 1));
        target.setGlowing(true);

        playShatterNovaAnimation(target.getLocation().add(0, target.getHeight() / 2, 0));

        UUID targetId = target.getUniqueId();
        markedTargets.add(targetId);
        new BukkitRunnable() {
            @Override
            public void run() {
                markedTargets.remove(targetId);
                if (target.isValid()) target.setGlowing(false);
            }
        }.runTaskLater(plugin, VULNERABLE_DURATION);
    }

    private void playShatterNovaAnimation(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.2f);
        int particleCount = 25;
        int animationTicks = 18;

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            BlockDisplay shard = location.getWorld().spawn(location, BlockDisplay.class, bd -> {
                bd.setBlock(SHATTER_MATERIALS.get(r.nextInt(SHATTER_MATERIALS.size())).createBlockData());
                bd.setInterpolationDuration(animationTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(r.nextFloat() * 0.6f + 0.3f);
                t.getLeftRotation().set(new AxisAngle4f(r.nextFloat() * 360, r.nextFloat(), r.nextFloat(), r.nextFloat()));
                bd.setTransformation(t);
            });

            Location endPos = location.clone().add(Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(3));
            shard.teleport(endPos);
            Transformation finalTransform = shard.getTransformation();
            finalTransform.getScale().set(0f);
            shard.setTransformation(finalTransform);

            new BukkitRunnable() { @Override public void run() { if (shard.isValid()) shard.remove(); }}.runTaskLater(plugin, animationTicks + 1);
        }
    }

    private void playResonanceImplosionAnimation(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 1.5f);
        int particleCount = 12;
        int animationTicks = 10;
        double radius = 1.5;

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            Location startPos = location.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            BlockDisplay crystal = location.getWorld().spawn(startPos, BlockDisplay.class, bd -> {
                bd.setBlock(Material.AMETHYST_BLOCK.createBlockData());
                bd.setInterpolationDuration(animationTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(0.5f);
                bd.setTransformation(t);
            });

            crystal.teleport(location);
            Transformation finalTransform = crystal.getTransformation();
            finalTransform.getScale().set(0f);
            crystal.setTransformation(finalTransform);

            new BukkitRunnable() { @Override public void run() { if (crystal.isValid()) crystal.remove(); }}.runTaskLater(plugin, animationTicks + 1);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) return;
        if (markedTargets.remove(target.getUniqueId())) {
            if (target instanceof Player p && trustManager.isTrusted(player.getUniqueId(), p.getUniqueId())) return;
            event.setDamage(event.getDamage() * (1 + DAMAGE_AMPLIFICATION));
            if (target.isValid()) target.setGlowing(false);

            playResonanceImplosionAnimation(target.getLocation().add(0, target.getHeight() / 2, 0));
        }
    }

    @EventHandler
    public void onEntityFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && dashingPlayers.contains(player.getUniqueId())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (!configManager.isTrimEnabled("spire")) return;
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }
}