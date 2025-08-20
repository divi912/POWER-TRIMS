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
import org.bukkit.block.Block;
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
import java.util.HashSet;
import java.util.Set;

public class TideTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;

    // --- CONSTANTS ---
    private final long TIDE_COOLDOWN;
    private final double WAVE_WIDTH;
    private final int EFFECT_DURATION_TICKS;
    private final double KNOCKBACK_STRENGTH;
    private final int WALL_HEIGHT;
    private final int MOVE_DELAY_TICKS;
    private final int MAX_MOVES;

    public TideTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;

        TIDE_COOLDOWN = configManager.getLong("tide.primary.cooldown", 120_000L);
        WAVE_WIDTH = configManager.getDouble("tide.primary.wave_width", 3.0);
        EFFECT_DURATION_TICKS = configManager.getInt("tide.primary.effect_duration_ticks", 300);
        KNOCKBACK_STRENGTH = configManager.getDouble("tide.primary.knockback_strength", 1.8);
        WALL_HEIGHT = configManager.getInt("tide.primary.wall_height", 6);
        MOVE_DELAY_TICKS = configManager.getInt("tide.primary.move_delay_ticks", 2);
        MAX_MOVES = configManager.getInt("tide.primary.max_moves", 20);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            activateTidePrimary(event.getPlayer());
        }
    }

    public void activateTidePrimary(Player player) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.TIDE) ||
                cooldownManager.isOnCooldown(player, TrimPattern.TIDE)) {
            return;
        }

        Location startLoc = player.getLocation();
        Vector direction = startLoc.getDirection().setY(0).normalize();
        World world = player.getWorld();

        world.playSound(startLoc, Sound.ENTITY_GENERIC_SPLASH, 2.0f, 0.5f);
        world.playSound(startLoc, Sound.BLOCK_WATER_AMBIENT, 2.0f, 1.0f);

        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());

        new BukkitRunnable() {
            private int moves = 0;
            private final Location currentLoc = startLoc.clone();
            private final Set<Block> previousWaterBlocks = new HashSet<>();
            private final Set<Block> currentWaterBlocks = new HashSet<>();

            @Override
            public void run() {
                // --- Cleanup old blocks ---
                // Clear the blocks from the wave two ticks ago
                clearWaterBlocks(previousWaterBlocks, world);

                // Move the "current" blocks to the "previous" list for the next cycle
                previousWaterBlocks.clear();
                previousWaterBlocks.addAll(currentWaterBlocks);
                currentWaterBlocks.clear();

                // --- Main Logic ---
                if (moves >= MAX_MOVES) {
                    // OPTIMIZATION: Simplified cleanup without nested tasks.
                    // Immediately clear the final set of blocks.
                    clearWaterBlocks(previousWaterBlocks, world);
                    world.playSound(currentLoc, Sound.WEATHER_RAIN, 1.5f, 1.0f);
                    this.cancel();
                    return;
                }

                // Move the wave's center forward
                currentLoc.add(direction);

                // --- Build new wave ---
                for (double w = -WAVE_WIDTH; w <= WAVE_WIDTH; w += 0.8) {
                    Location wallBaseLoc = currentLoc.clone().add(perpendicular.clone().multiply(w));
                    for (int h = 0; h < WALL_HEIGHT; h++) {
                        Block block = wallBaseLoc.clone().add(0, h, 0).getBlock();
                        if (block.getType().isAir()) {
                            block.setType(Material.WATER, false); // `false` is a crucial optimization
                            currentWaterBlocks.add(block);
                        } else if (!block.getType().isSolid()) {
                            // Also replace non-solid blocks like grass
                            block.setType(Material.WATER, false);
                            currentWaterBlocks.add(block);
                        } else {
                            break; // Stop building this column if we hit a solid block
                        }
                    }
                }

                // --- Affect entities ---
                // OPTIMIZATION: Use getNearbyLivingEntities for efficiency.
                for (LivingEntity target : world.getNearbyLivingEntities(currentLoc, WAVE_WIDTH + 1.5, WALL_HEIGHT, WAVE_WIDTH + 1.5)) {
                    if (target.equals(player)) continue;
                    if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) continue;

                    Vector knockback = direction.clone().multiply(KNOCKBACK_STRENGTH);
                    knockback.setY(0.4);
                    target.setVelocity(knockback);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_DURATION_TICKS, 1));

                    world.spawnParticle(Particle.SPLASH, target.getLocation(), 15, 0.4, 1, 0.4, 0.2);
                    world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.2f);
                }

                world.playSound(currentLoc, Sound.BLOCK_WATER_AMBIENT, 0.8f, 1.2f);
                moves++;
            }
        }.runTaskTimer(plugin, 0L, MOVE_DELAY_TICKS);

        player.sendMessage(ChatColor.AQUA + "§8[§bTide§8] You have summoned a massive tidal wall!");
        cooldownManager.setCooldown(player, TrimPattern.TIDE, TIDE_COOLDOWN);
    }

    /**
     * Helper method to clear a set of blocks and play a particle effect.
     * @param blocks The set of blocks to turn into air.
     * @param world The world to play effects in.
     */
    private void clearWaterBlocks(Set<Block> blocks, World world) {
        for (Block block : blocks) {
            // Check if the block is still water before changing it
            if (block.getType() == Material.WATER) {
                block.setType(Material.AIR);
                world.spawnParticle(Particle.SPLASH, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }
}
