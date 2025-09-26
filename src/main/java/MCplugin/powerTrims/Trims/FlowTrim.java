package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.ConfigManager;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flow Trim Ability: Toggleable Gale Dash costs health continuously until deactivated or duration expires.
 */
public class FlowTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final ConfigManager configManager;
    private final NamespacedKey effectKey;
    private final NamespacedKey dashEndFallImmunityKey;


    // --- CONSTANTS ---
    private final int HEART_COST_INTERVAL;
    private final double HEART_COST_AMOUNT;
    private final long DASH_COOLDOWN;
    // --- NEW: Added duration constant ---
    private final int DASH_DURATION;

    private final Map<UUID, BukkitRunnable> dashTasks = new HashMap<>();
    private final Map<UUID, Boolean> isDashing = new HashMap<>();

    public FlowTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.configManager = configManager;
        this.effectKey = new NamespacedKey(plugin, "flow_trim_effect");
        this.dashEndFallImmunityKey = new NamespacedKey(plugin, "flow_trim_dash_end_immune");


        HEART_COST_INTERVAL = configManager.getInt("flow.primary.heart_cost_interval", 20);
        HEART_COST_AMOUNT = configManager.getDouble("flow.primary.heart_cost_amount", 2.0);
        DASH_COOLDOWN = configManager.getLong("flow.primary.cooldown", 60000);
        DASH_DURATION = configManager.getInt("flow.primary.duration", 400);
    }



    public void FlowPrimary(Player player) {
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

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
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

                // --- NEW: Deactivate if duration runs out ---
                if (tickCount >= DASH_DURATION) {
                    deactivateDash(player, "Gale Dash has expired.");
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
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setAllowFlight(false);
        }
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
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (!configManager.isTrimEnabled("flow")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            FlowPrimary(event.getPlayer());
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
