package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class CoastTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    public CoastTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        abilityManager.registerPrimaryAbility(TrimPattern.COAST, this::CoastPrimary);
    }

    // Activates the Coast Trim ability: Water Burst
    public void CoastPrimary(Player player) {
        if (!configManager.isTrimEnabled("coast")) {
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.COAST)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.COAST)) return;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        // Play activation sounds and show particles
        playEffects(playerLoc, world);

        int waterBurstRadius = configManager.getInt("coast.primary.water-burst-radius");
        int waterBurstDamage = configManager.getInt("coast.primary.water-burst-damage");
        long waterBurstCooldown = configManager.getLong("coast.primary.water-burst-cooldown");
        int pullDurationTicks = configManager.getInt("coast.primary.pull-duration-ticks");
        int debuffDurationTicks = configManager.getInt("coast.primary.debuff-duration-ticks");
        int buffDurationTicks = configManager.getInt("coast.primary.buff-duration-ticks");
        int weaknessAmplifier = configManager.getInt("coast.primary.weakness-amplifier");
        int slownessAmplifier = configManager.getInt("coast.primary.slowness-amplifier");
        int speedAmplifier = configManager.getInt("coast.primary.speed-amplifier");
        int resistanceAmplifier = configManager.getInt("coast.primary.resistance-amplifier");

        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(playerLoc, waterBurstRadius, waterBurstRadius, waterBurstRadius)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue;
                }

                target.damage(waterBurstDamage, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, debuffDurationTicks, weaknessAmplifier));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, debuffDurationTicks, slownessAmplifier));
                world.spawnParticle(Particle.SPLASH, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

                targets.add(target);
            }
        }

        if (!targets.isEmpty()) {
            startPullTask(targets, player, pullDurationTicks);
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, buffDurationTicks, speedAmplifier));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, buffDurationTicks, resistanceAmplifier));

        cooldownManager.setCooldown(player, TrimPattern.COAST, waterBurstCooldown);

        Messaging.sendTrimMessage(player, "Coast", ChatColor.DARK_AQUA, "You have activated " + ChatColor.AQUA + "Water Burst!");
    }

    private void playEffects(Location location, World world) {
        world.playSound(location, Sound.ENTITY_DOLPHIN_PLAY, 1.0f, 1.5f);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);

        for (double angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            double offsetX = Math.cos(rad) * 1.5;
            double offsetZ = Math.sin(rad) * 1.5;
            Location effectLoc = location.clone().add(offsetX, 0.5, offsetZ);
            world.spawnParticle(Particle.FALLING_WATER, effectLoc, 20, 0.1, 0.1, 0.1, 0.1);
        }

        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 1, 0), 50, 1, 0.5, 1, 0.1);
    }

    private void startPullTask(List<LivingEntity> targets, Player user, int pullDurationTicks) {
        new BukkitRunnable() {
            private int ticksLived = 0;

            @Override
            public void run() {
                if (ticksLived++ > pullDurationTicks || !user.isOnline()) {
                    this.cancel();
                    return;
                }

                targets.removeIf(t -> !t.isValid() || t.isDead());

                if (targets.isEmpty()){
                    this.cancel();
                    return;
                }

                Location userLocation = user.getLocation();

                for (LivingEntity target : targets) {
                    if (target.getLocation().distanceSquared(userLocation) < 4) { 
                        continue;
                    }

                    Vector pullDirection = userLocation.toVector().subtract(target.getLocation().toVector()).normalize();
                    target.setVelocity(pullDirection);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }
}
