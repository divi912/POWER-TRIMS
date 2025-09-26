/*
 * This file is part of [ POWER TRIMS ].
 *
 * [POWER TRIMS] is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * [ POWER TRIMS ] is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with [Your Plugin Name].  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) [2025] [ div ].
 */


package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.ConfigManager;
import MCplugin.powerTrims.Logic.PersistentTrustManager;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EyeTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;

    // --- CONSTANTS ---
    private final double TRUE_SIGHT_RADIUS;
    private final int TRUE_SIGHT_DURATION_TICKS;
    private final long TRUE_SIGHT_COOLDOWN;
    private final long TASK_INTERVAL_TICKS;
    private final double TRUE_SIGHT_VERTICAL_RADIUS;

    // --- STATE MANAGEMENT ---
    private final Map<UUID, BukkitRunnable> activeTrueSightTasks = new HashMap<>();

    public EyeTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;

        TRUE_SIGHT_RADIUS = configManager.getDouble("eye.primary.true_sight_radius", 80.0);
        TRUE_SIGHT_DURATION_TICKS = configManager.getInt("eye.primary.true_sight_duration_ticks", 600);
        TRUE_SIGHT_COOLDOWN = configManager.getLong("trim.eye.primary.cooldown", 120_000L);
        TASK_INTERVAL_TICKS = configManager.getLong("eye.primary.task_interval_ticks", 20L);
        TRUE_SIGHT_VERTICAL_RADIUS = configManager.getDouble("eye.primary.true_sight_vertical_radius", 50.0);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (!configManager.isTrimEnabled("eye")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            activateEyePrimary(event.getPlayer());
        }
    }

    public void activateEyePrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.EYE)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.EYE)) return;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
            return;
        }

        // Cancel any existing task for this player to prevent duplicates
        activeTrueSightTasks.computeIfPresent(player.getUniqueId(), (uuid, task) -> {
            task.cancel();
            return null;
        });

        // Activation effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 2.0f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        createEyeEffect(player, true); // Initial, more intense effect

        // This set will track entities affected ONLY during this activation
        final Set<UUID> affectedEntities = new HashSet<>();

        BukkitRunnable trueSightTask = new BukkitRunnable() {
            private int ticksRun = 0;

            @Override
            public void run() {
                // Stop condition: time is up or player logged off
                if (ticksRun >= TRUE_SIGHT_DURATION_TICKS || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // OPTIMIZATION: Scan for nearby entities
                for (Entity entity : player.getNearbyEntities(TRUE_SIGHT_RADIUS, TRUE_SIGHT_VERTICAL_RADIUS, TRUE_SIGHT_RADIUS)) {
                    if (entity instanceof LivingEntity target && !target.equals(player)) {
                        // Skip trusted players
                        if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                            continue;
                        }

                        // THE KEY OPTIMIZATION: Only affect each entity ONCE by adding its UUID to a set.
                        // The .add() method returns true only if the element was not already in the set.
                        if (affectedEntities.add(target.getUniqueId())) {
                            applyDebuffs(target);
                        }
                    }
                }

                // Pulse the visual effect
                if(ticksRun % (TASK_INTERVAL_TICKS * 5) == 0){ // Every 5 seconds
                    createEyeEffect(player, false);
                }

                ticksRun += (int) TASK_INTERVAL_TICKS;
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                // Clean up the map once the task is truly cancelled
                activeTrueSightTasks.remove(player.getUniqueId());
            }
        };

        // Store and run the task
        activeTrueSightTasks.put(player.getUniqueId(), trueSightTask);
        // OPTIMIZATION: Run task once per second, not 10 times per second
        trueSightTask.runTaskTimer(plugin, 0L, TASK_INTERVAL_TICKS);

        cooldownManager.setCooldown(player, TrimPattern.EYE, TRUE_SIGHT_COOLDOWN);
        player.sendMessage("§8[§bEye§8] §7True Sight activated!");
    }

    /**
     * Applies the suite of debuffs to a target.
     * @param target The LivingEntity to affect.
     */
    private void applyDebuffs(LivingEntity target) {
        // Remove invisibility to reveal the target
        target.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Apply effects
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 1200, 0, false, false)); // 1 minute
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 0, false, false)); // 30 seconds
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 1, false, false)); // 30 seconds
    }

    /**
     * Creates the visual particle effect for the ability.
     * @param player The player to center the effect on.
     * @param isInitialActivation If true, a more intense version of the effect is played.
     */
    private void createEyeEffect(Player player, boolean isInitialActivation) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // OPTIMIZATION: Reduced the radius from an invisible 80 blocks to a visible 15 blocks.
        double effectRadius = 15.0;

        // This effect is intense, so only play it on the initial cast.
        if(isInitialActivation) {
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 20) {
                double x = Math.cos(angle) * effectRadius;
                double z = Math.sin(angle) * effectRadius;
                Location pLoc = loc.clone().add(x, 0.1, z);
                world.spawnParticle(Particle.SMOKE, pLoc, 3, 0, 0, 0, 0.05);
                world.spawnParticle(Particle.DUST, pLoc, 2, new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.2f));
            }
        }

        // Central glowing effect (the "eye")
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.2, 0), 20, new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.5f));
        world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1.2, 0), 10, 0.5, 0.5, 0.5, 0.05);
    }
}
