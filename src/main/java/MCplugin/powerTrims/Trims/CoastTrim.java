package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.PersistentTrustManager;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
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

    // --- Ability Constants ---
    // Behavior
    private static final int WATER_BURST_RADIUS = 30;
    private static final int WATER_BURST_DAMAGE = 10;
    private static final long WATER_BURST_COOLDOWN = 60000; // 60 seconds
    private static final int PULL_DURATION_TICKS = 60;    // 3 seconds

    // Potion Effects
    private static final int DEBUFF_DURATION_TICKS = 80;    // 4 seconds
    private static final int BUFF_DURATION_TICKS = 100;     // 5 seconds
    private static final int WEAKNESS_AMPLIFIER = 1;
    private static final int SLOWNESS_AMPLIFIER = 1;
    private static final int SPEED_AMPLIFIER = 1;
    private static final int RESISTANCE_AMPLIFIER = 0;

    // Trigger
    private static final int ACTIVATION_SLOT = 8; // The 9th hotbar slot

    public CoastTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
    }

    // Activates the Coast Trim ability: Water Burst
    public void CoastPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.COAST)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.COAST)) return;

        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        // Play activation sounds and show particles
        playEffects(playerLoc, world);

        // Collect all valid targets into a list
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(playerLoc, WATER_BURST_RADIUS, WATER_BURST_RADIUS, WATER_BURST_RADIUS)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                // Skip trusted players
                if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue;
                }

                // Apply instant damage and debuffs
                target.damage(WATER_BURST_DAMAGE, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, DEBUFF_DURATION_TICKS, WEAKNESS_AMPLIFIER));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DEBUFF_DURATION_TICKS, SLOWNESS_AMPLIFIER));
                world.spawnParticle(Particle.SPLASH, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

                targets.add(target);
            }
        }

        // Start ONE consolidated task to pull all collected targets
        if (!targets.isEmpty()) {
            startPullTask(targets, player);
        }

        // Apply buffs to the player
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BUFF_DURATION_TICKS, SPEED_AMPLIFIER));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, BUFF_DURATION_TICKS, RESISTANCE_AMPLIFIER));

        // Set ability cooldown
        cooldownManager.setCooldown(player, TrimPattern.COAST, WATER_BURST_COOLDOWN);

        // Send activation message
        sendActivationMessage(player);
    }

    private void playEffects(Location location, World world) {
        world.playSound(location, Sound.ENTITY_DOLPHIN_PLAY, 1.0f, 1.5f);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);

        // Water particle ring
        for (double angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            double offsetX = Math.cos(rad) * 1.5;
            double offsetZ = Math.sin(rad) * 1.5;
            Location effectLoc = location.clone().add(offsetX, 0.5, offsetZ);
            world.spawnParticle(Particle.FALLING_WATER, effectLoc, 20, 0.1, 0.1, 0.1, 0.1);
        }

        // Cloud effect
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 1, 0), 50, 1, 0.5, 1, 0.1);
    }

    private void startPullTask(List<LivingEntity> targets, Player user) {
        new BukkitRunnable() {
            private int ticksLived = 0;

            @Override
            public void run() {
                // Stop the task if the user is offline or the duration is over
                if (ticksLived++ > PULL_DURATION_TICKS || !user.isOnline()) {
                    this.cancel();
                    return;
                }

                // Remove invalid targets to prevent errors and improve performance
                targets.removeIf(t -> !t.isValid() || t.isDead());

                if (targets.isEmpty()){
                    this.cancel();
                    return;
                }

                Location userLocation = user.getLocation();

                for (LivingEntity target : targets) {
                    // Stop pulling an entity if it gets close (using distanceSquared is more performant)
                    if (target.getLocation().distanceSquared(userLocation) < 4) { // 2*2 = 4
                        continue;
                    }

                    Vector pullDirection = userLocation.toVector().subtract(target.getLocation().toVector()).normalize();
                    target.setVelocity(pullDirection);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick
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

    // Listens for the player hotbar switch + sneak combo to activate Water Burst
    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == ACTIVATION_SLOT) {
            CoastPrimary(player);
        }
    }
}