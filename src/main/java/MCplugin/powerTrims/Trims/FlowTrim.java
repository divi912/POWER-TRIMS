package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flow Trim Ability: Toggleable Gale Dash costs health continuously until deactivated.
 */
public class FlowTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final NamespacedKey effectKey;
    private final NamespacedKey dashEndFallImmunityKey;

    // Heart cost settings
    private static final int HEART_COST_INTERVAL = 20;       // ticks (1 second)
    private static final double HEART_COST_AMOUNT = 2.0;     // HP (1 heart)
    private static final long DASH_COOLDOWN = 60000;         // ms

    private final Map<UUID, BukkitRunnable> dashTasks = new HashMap<>();
    private final Map<UUID, Boolean> isDashing = new HashMap<>();

    public FlowTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.effectKey = new NamespacedKey(plugin, "flow_trim_effect");
        this.dashEndFallImmunityKey = new NamespacedKey(plugin, "flow_trim_dash_end_immune");
        FlowPassive();
    }

    private void FlowPassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.FLOW)) {
                    if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false, true));
                        player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                    }
                } else {
                    if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                        player.removePotionEffect(PotionEffectType.SPEED);
                        player.getPersistentDataContainer().remove(effectKey);
                    }
                }
            }
        }, 0L, 20L);
    }

    public void toggleDash(Player player) {
        UUID id = player.getUniqueId();
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.FLOW)) return;

        // Deactivate if already dashing
        if (isDashing.getOrDefault(id, false)) {
            deactivateDash(player, "You have deactivated §bGale Dash§7.");
            return;
        }

        // Cannot activate if on cooldown
        if (cooldownManager.isOnCooldown(player, TrimPattern.FLOW)) {
            player.sendMessage("§8[§bFlow§8] §7Ability on cooldown!");
            return;
        }
        player.setAllowFlight(true);
        // Activation effects
        Location loc = player.getLocation();
        World world = player.getWorld();
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.5f);
        createWindEffect(player);

        // Start dash task
        BukkitRunnable task = new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !isDashing.getOrDefault(id, false)) {
                    this.cancel();
                    return;
                }

                // Drain health each interval
                if (tickCount % HEART_COST_INTERVAL == 0) {
                    double hp = player.getHealth();
                    if (hp > HEART_COST_AMOUNT) {
                        player.setHealth(hp - HEART_COST_AMOUNT);
                    } else {
                        player.sendMessage("§8[§bFlow§8] §cNot enough health to maintain Gale Dash!");
                        deactivateDash(player, "Gale Dash ended due to low health.");
                        this.cancel();
                        return;
                    }
                }

                // Propel and particle
                Vector dir = player.getLocation().getDirection().normalize();
                player.setVelocity(dir.multiply(1.2));
                createWindEffect(player);

                tickCount++;
            }
        };

        isDashing.put(id, true);
        dashTasks.put(id, task);
        task.runTaskTimer(plugin, 0L, 1L);
        player.sendMessage("§8[§bFlow§8] §7Activated §bGale Dash§7! §7(uses " + (HEART_COST_AMOUNT/2) + " ❤/sec)");
    }

    private void deactivateDash(Player player, String message) {
        UUID id = player.getUniqueId();
        if (dashTasks.containsKey(id)) {
            dashTasks.get(id).cancel();
            dashTasks.remove(id);
        }
        isDashing.put(id, false);
        player.setAllowFlight(false);
        cooldownManager.setCooldown(player, TrimPattern.FLOW, DASH_COOLDOWN);
        player.sendMessage("§8[§bFlow§8] §7" + message + " Cooldown applied.");
        player.getPersistentDataContainer().set(dashEndFallImmunityKey, PersistentDataType.BYTE, (byte) 1); // Set the flag
    }

    private void createWindEffect(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            double x = Math.cos(angle) * 0.5;
            double z = Math.sin(angle) * 0.5;
            world.spawnParticle(Particle.CLOUD, loc.clone().add(-x, 0.5, -z), 0, 0, 0, 0, 0.05);
        }
        world.spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            toggleDash(player);
        }
    }

    @EventHandler
    public void onEntityFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && isDashing.getOrDefault(player.getUniqueId(), false)) {
                event.setCancelled(true);
            } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL && player.getPersistentDataContainer().has(dashEndFallImmunityKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                player.getPersistentDataContainer().remove(dashEndFallImmunityKey); // Remove the flag
            }
        }
    }
}