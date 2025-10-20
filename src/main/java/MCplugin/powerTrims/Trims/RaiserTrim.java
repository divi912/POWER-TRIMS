package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RaiserTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    private final long SURGE_COOLDOWN;
    private final double ENTITY_PULL_RADIUS;
    private final double PLAYER_UPWARD_BOOST;
    private final int PEARL_COOLDOWN_TICKS;
    private static final List<Material> PILLAR_MATERIALS = List.of(Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.DEEPSLATE_BRICKS);
    private static final List<Material> SHATTER_MATERIALS = List.of(Material.DEEPSLATE_TILES, Material.COBBLED_DEEPSLATE, Material.PURPUR_BLOCK);
    private static final List<Material> EARTHQUAKE_MATERIALS = List.of(Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.OBSIDIAN);


    private final Set<UUID> awaitingLanding = new HashSet<>();

    public RaiserTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        SURGE_COOLDOWN = configManager.getLong("raiser.primary.cooldown");
        ENTITY_PULL_RADIUS = configManager.getDouble("raiser.primary.entity_pull_radius");
        PLAYER_UPWARD_BOOST = configManager.getDouble("raiser.primary.player_upward_boost");
        PEARL_COOLDOWN_TICKS = configManager.getInt("raiser.primary.pearl_cooldown_ticks");

        abilityManager.registerPrimaryAbility(TrimPattern.RAISER, this::activateRaiserPrimary);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateRaiserPrimary(Player player) {
        if (!configManager.isTrimEnabled("raiser")) return;
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RAISER) || cooldownManager.isOnCooldown(player, TrimPattern.RAISER)) return;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        cooldownManager.setCooldown(player, TrimPattern.RAISER, SURGE_COOLDOWN);

        player.setVelocity(new Vector(0, PLAYER_UPWARD_BOOST, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.0f);

        playGeyserLaunchAnimation(player);

        Messaging.sendTrimMessage(player, "Raiser", ChatColor.GOLD, "Raiser's Surge activated!");
        awaitingLanding.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> awaitingLanding.remove(player.getUniqueId()), 100L);
    }

    private void triggerLandingEffect(Player player) {
        Location landingLoc = player.getLocation();
        World world = player.getWorld();

        world.playSound(landingLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        playLandingAnimation(landingLoc);

        for (LivingEntity target : world.getNearbyLivingEntities(landingLoc, ENTITY_PULL_RADIUS)) {
            if (target.equals(player) || (target instanceof Player p && trustManager.isTrusted(player.getUniqueId(), p.getUniqueId()))) continue;


            playEarthquakeAnimation(target);

            Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.5);
            pull.setY(1.2);
            target.setVelocity(pull);

            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 2));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0));

            if (target instanceof Player targetPlayer) {
                targetPlayer.setCooldown(Material.ENDER_PEARL, PEARL_COOLDOWN_TICKS);
                Messaging.sendTrimMessage(targetPlayer, "Raiser", ChatColor.DARK_PURPLE, "Raiser's Surge disrupted your teleportation!");
            }
        }
    }

    private void playEarthquakeAnimation(LivingEntity target) {
        Location center = target.getLocation();
        target.getWorld().playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 1.5f, 0.6f);

        int particleCount = 30;
        int animationTicks = 20;

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();


            BlockDisplay shard = target.getWorld().spawn(center, BlockDisplay.class, bd -> {
                bd.setBlock(EARTHQUAKE_MATERIALS.get(r.nextInt(EARTHQUAKE_MATERIALS.size())).createBlockData());
                bd.setInterpolationDuration(animationTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(r.nextFloat() * 0.7f + 0.3f);
                t.getLeftRotation().set(new AxisAngle4f(r.nextFloat() * 360, r.nextFloat(), r.nextFloat(), r.nextFloat()));
                bd.setTransformation(t);
            });

            Vector direction = new Vector(r.nextDouble() - 0.5, r.nextDouble(0.8, 1.2), r.nextDouble() - 0.5).normalize();
            Location finalPos = center.clone().add(direction.multiply(r.nextDouble(2.0, 3.5)));

            shard.teleport(finalPos);
            Transformation finalTransform = shard.getTransformation();
            finalTransform.getScale().set(0f);
            shard.setTransformation(finalTransform);

            new BukkitRunnable() { @Override public void run() { shard.remove(); }}.runTaskLater(plugin, animationTicks + 1);
        }
    }


    private void playGeyserLaunchAnimation(Player player) {
        Location center = player.getLocation();
        int particleCount = 20;
        int animationTicks = 15;

        player.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, center, 1, 0, 0, 0, 0);

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            BlockDisplay particle = player.getWorld().spawn(center, BlockDisplay.class, bd -> {
                bd.setBlock(Material.WHITE_WOOL.createBlockData());
                bd.setInterpolationDuration(animationTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(r.nextFloat() * 0.5f + 0.2f);
                bd.setTransformation(t);
            });

            double angle = r.nextDouble(Math.PI * 4);
            double radius = r.nextDouble(1.5, 3.0);
            Location finalPos = center.clone().add(Math.cos(angle) * radius, 4.0, Math.sin(angle) * radius);

            particle.teleport(finalPos);
            Transformation finalTransform = particle.getTransformation();
            finalTransform.getScale().set(0f);
            particle.setTransformation(finalTransform);

            new BukkitRunnable() { @Override public void run() { particle.remove(); }}.runTaskLater(plugin, animationTicks + 1);
        }
    }

    private void playLandingAnimation(Location center) {
        int particleCount = 60;
        int animationTicks = 25;
        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Material material = SHATTER_MATERIALS.get(r.nextInt(SHATTER_MATERIALS.size()));
            BlockDisplay shard = center.getWorld().spawn(center, BlockDisplay.class, bd -> {
                bd.setBlock(material.createBlockData());
                bd.setInterpolationDuration(animationTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(r.nextFloat() * 0.6f + 0.3f);
                t.getLeftRotation().set(new AxisAngle4f(r.nextFloat() * 360, r.nextFloat(), r.nextFloat(), r.nextFloat()));
                bd.setTransformation(t);
            });
            double angle = r.nextDouble(Math.PI * 2);
            double distance = r.nextDouble(ENTITY_PULL_RADIUS * 0.8, ENTITY_PULL_RADIUS);
            Location finalPos = center.clone().add(Math.cos(angle) * distance, r.nextDouble(0.3), Math.sin(angle) * distance);
            shard.teleport(finalPos);
            Transformation finalTransform = shard.getTransformation();
            finalTransform.getScale().set(0f);
            shard.setTransformation(finalTransform);
            new BukkitRunnable() { @Override public void run() { shard.remove(); }}.runTaskLater(plugin, animationTicks + 1);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isOnGround() && awaitingLanding.remove(player.getUniqueId())) {
            triggerLandingEffect(player);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL &&
                ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RAISER)) {
            event.setCancelled(true);
        }
    }
}
