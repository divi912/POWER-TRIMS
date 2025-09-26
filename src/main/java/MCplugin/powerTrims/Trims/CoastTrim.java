package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.ConfigManager;
import MCplugin.powerTrims.Logic.PersistentTrustManager;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
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
    private final int activationSlot;

    public CoastTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.activationSlot = plugin.getConfig().getInt("activation-slot", 8);
    }

    // Activates the Coast Trim ability: Water Burst
    public void CoastPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.COAST)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.COAST)) return;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        // Play activation sounds and show particles
        playEffects(playerLoc, world);

        int waterBurstRadius = configManager.getInt("coast.primary.water-burst-radius", 30);
        int waterBurstDamage = configManager.getInt("coast.primary.water-burst-damage", 10);
        long waterBurstCooldown = configManager.getLong("coast.primary.water-burst-cooldown", 60000);
        int pullDurationTicks = configManager.getInt("coast.primary.pull-duration-ticks", 60);
        int debuffDurationTicks = configManager.getInt("coast.primary.debuff-duration-ticks", 80);
        int buffDurationTicks = configManager.getInt("coast.primary.buff-duration-ticks", 100);
        int weaknessAmplifier = configManager.getInt("coast.primary.weakness-amplifier", 1);
        int slownessAmplifier = configManager.getInt("coast.primary.slowness-amplifier", 1);
        int speedAmplifier = configManager.getInt("coast.primary.speed-amplifier", 1);
        int resistanceAmplifier = configManager.getInt("coast.primary.resistance-amplifier", 0);

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

        sendActivationMessage(player);
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

    private void sendActivationMessage(Player player) {
        Component message = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("Coast", NamedTextColor.DARK_AQUA))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text("You have activated ", NamedTextColor.GRAY))
                .append(Component.text("Water Burst", NamedTextColor.AQUA))
                .append(Component.text("!", NamedTextColor.GRAY));
        player.sendMessage(message);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (!configManager.isTrimEnabled("coast")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            CoastPrimary(event.getPlayer());
        }
    }
}
