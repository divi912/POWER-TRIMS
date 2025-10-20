package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;


import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WardTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    // --- CONSTANTS ---
    private final int BARRIER_DURATION;
    private final int ABSORPTION_LEVEL;
    private final int RESISTANCE_BOOST_LEVEL;
    private final long WARD_COOLDOWN;
    private final Set<UUID> activeBarriers = new HashSet<>();

    public WardTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        BARRIER_DURATION = configManager.getInt("ward.primary.barrier_duration");
        ABSORPTION_LEVEL = configManager.getInt("ward.primary.absorption_level");
        RESISTANCE_BOOST_LEVEL = configManager.getInt("ward.primary.resistance_boost_level");
        WARD_COOLDOWN = configManager.getLong("ward.primary.cooldown");

        abilityManager.registerPrimaryAbility(TrimPattern.WARD, this::WardPrimary);
    }

    public void WardPrimary(Player player) {
        if (!configManager.isTrimEnabled("ward")) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WARD) || cooldownManager.isOnCooldown(player, TrimPattern.WARD)) {
            return;
        }

        // Visual and sound effects
        Location playerLoc = player.getLocation();
        player.getWorld().playSound(playerLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        player.getWorld().playSound(playerLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        player.getWorld().playSound(playerLoc, Sound.BLOCK_ANVIL_USE, 0.7f, 1.5f);

        // Apply enhanced protection effects to the player
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, BARRIER_DURATION, ABSORPTION_LEVEL, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, BARRIER_DURATION, RESISTANCE_BOOST_LEVEL, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, BARRIER_DURATION, 0, true, true, true));

        // Create particle effect
        createBarrierEffect(player);

        // Add player to active barriers set
        activeBarriers.add(player.getUniqueId());

        // Schedule removal
        new BukkitRunnable() {
            @Override
            public void run() {
                activeBarriers.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, BARRIER_DURATION);

        Messaging.sendTrimMessage(player, "Ward", ChatColor.YELLOW, "You have activated your personal Protective Barrier!");
        cooldownManager.setCooldown(player, TrimPattern.WARD, WARD_COOLDOWN);

        // Create continuous particle effect around the player
        new BukkitRunnable() {
            int remainingTicks = BARRIER_DURATION;

            @Override
            public void run() {
                if (remainingTicks <= 0 || !activeBarriers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }

                Location loc = player.getLocation();
                World world = player.getWorld();

                // Create a spiral effect around the player
                double y = 0;
                for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
                    double x = Math.cos(i + (remainingTicks * 0.1)) * 1.2;
                    double z = Math.sin(i + (remainingTicks * 0.1)) * 1.2;
                    Location particleLoc = loc.clone().add(x, y, z);
                    world.spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                    y += 0.2;
                    if (y > 2) y = 0;
                }

                remainingTicks--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createBarrierEffect(Player player) {
        Location center = player.getLocation();
        World world = player.getWorld();

        // Create dome effect
        new BukkitRunnable() {
            double phi = 0;

            @Override
            public void run() {
                phi += Math.PI / 8;
                if (phi >= Math.PI) {
                    this.cancel();
                    return;
                }

                for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 8) {
                    double x = 1.5 * Math.sin(phi) * Math.cos(theta);
                    double y = 1.5 * Math.cos(phi) + 1;
                    double z = 1.5 * Math.sin(phi) * Math.sin(theta);

                    Location particleLoc = center.clone().add(x, y, z);
                    world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
