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
import MCplugin.powerTrims.Logic.PersistentTrustManager; 
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
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
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final NamespacedKey effectKey;
    private static final double WAVE_WIDTH = 3.0;
    private static final int EFFECT_DURATION = 300;
    private static final double KNOCKBACK_STRENGTH = 1.8;
    private static final int WALL_HEIGHT = 6;
    private static final int MOVE_DELAY = 2;
    private static final int MAX_MOVES = 20;

    public TideTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.effectKey = new NamespacedKey(plugin, "tide_trim_effect");
    }


    public void TidePrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.TIDE)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.TIDE)) {
            return;
        }

        Location startLoc = player.getLocation();
        Vector direction = startLoc.getDirection().setY(0).normalize();
        World world = player.getWorld();

        world.playSound(startLoc, Sound.ENTITY_GENERIC_SPLASH, 2.0f, 0.5f);
        world.playSound(startLoc, Sound.BLOCK_WATER_AMBIENT, 2.0f, 1.0f);
        world.playSound(startLoc, Sound.ENTITY_PLAYER_SPLASH, 2.0f, 0.8f);

        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());
        Set<Block> currentWaterBlocks = new HashSet<>();
        Set<Block> previousWaterBlocks = new HashSet<>();
        Player tideUser = player; // Store the player using the ability

        new BukkitRunnable() {
            private int moves = 0;
            private Location currentLoc = startLoc.clone();

            @Override
            public void run() {
                for (Block block : previousWaterBlocks) {
                    block.setType(Material.AIR);
                    Location particleLoc = block.getLocation().add(0.5, 0.5, 0.5);
                    world.spawnParticle(Particle.SPLASH, particleLoc, 8, 0.3, 0.3, 0.3, 0.1);
                    world.spawnParticle(Particle.BUBBLE, particleLoc, 5, 0.2, 0.2, 0.2, 0.05);
                }
                previousWaterBlocks.clear();
                previousWaterBlocks.addAll(currentWaterBlocks);
                currentWaterBlocks.clear();

                currentLoc.add(direction);

                for (double w = -WAVE_WIDTH; w <= WAVE_WIDTH; w += 0.8) {
                    Location wallBaseLoc = currentLoc.clone().add(perpendicular.clone().multiply(w));

                    for (int h = 0; h < WALL_HEIGHT; h++) {
                        Location blockLoc = wallBaseLoc.clone().add(0, h, 0);
                        Block block = blockLoc.getBlock();

                        if (block.getType().isAir() || !block.getType().isSolid()) {
                            currentWaterBlocks.add(block);
                            block.setType(Material.WATER, false);

                            Location particleLoc = blockLoc.add(0.5, 0.5, 0.5);
                            world.spawnParticle(Particle.SPLASH, particleLoc, 5, 0.3, 0.3, 0.3, 0.1);
                            world.spawnParticle(Particle.BUBBLE, particleLoc, 3, 0.2, 0.2, 0.2, 0.05);
                            world.spawnParticle(Particle.BUBBLE_POP, particleLoc, 3, 0.2, 0.2, 0.2, 0.1);

                            if (w == -WAVE_WIDTH || w == WAVE_WIDTH || h == 0 || h == WALL_HEIGHT - 1) {
                                world.spawnParticle(Particle.SPLASH, particleLoc, 8, 0.2, 0.2, 0.2, 0.1);
                            }
                        } else if (!block.getType().equals(Material.WATER)) {
                            break;
                        }
                    }
                }

                for (Entity entity : world.getNearbyEntities(currentLoc, WAVE_WIDTH + 1.5, WALL_HEIGHT, WAVE_WIDTH + 1.5)) {
                    if (entity instanceof LivingEntity target && entity != tideUser) {
                        if (target instanceof Player targetPlayer && trustManager.isTrusted(tideUser.getUniqueId(), targetPlayer.getUniqueId())) {
                            continue; // Skip trusted players
                        }
                        Vector knockback = direction.clone().multiply(KNOCKBACK_STRENGTH);
                        knockback.setY(0.4);
                        target.setVelocity(knockback);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_DURATION, 1));

                        Location hitLoc = target.getLocation();
                        world.spawnParticle(Particle.SPLASH, hitLoc, 15, 0.4, 1, 0.4, 0.2);
                        world.spawnParticle(Particle.BUBBLE_POP, hitLoc, 10, 0.3, 0.8, 0.3, 0.1);
                        world.playSound(hitLoc, Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.2f);
                    }
                }

                world.playSound(currentLoc, Sound.BLOCK_WATER_AMBIENT, 0.8f, 1.2f);

                moves++;
                if (moves >= MAX_MOVES) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (Block block : currentWaterBlocks) {
                                block.setType(Material.AIR);
                                Location particleLoc = block.getLocation().add(0.5, 0.5, 0.5);
                                world.spawnParticle(Particle.SPLASH, particleLoc, 10, 0.3, 0.3, 0.3, 0.1);
                                world.spawnParticle(Particle.CLOUD, particleLoc, 5, 0.2, 0.2, 0.2, 0.05);
                                world.spawnParticle(Particle.BUBBLE, particleLoc, 8, 0.3, 0.3, 0.3, 0.1);
                            }
                            for (Block block : previousWaterBlocks) {
                                block.setType(Material.AIR);
                                Location particleLoc = block.getLocation().add(0.5, 0.5, 0.5);
                                world.spawnParticle(Particle.SPLASH, particleLoc, 10, 0.3, 0.3, 0.3, 0.1);
                                world.spawnParticle(Particle.CLOUD, particleLoc, 5, 0.2, 0.2, 0.2, 0.05);
                                world.spawnParticle(Particle.BUBBLE, particleLoc, 8, 0.3, 0.3, 0.3, 0.1);
                            }
                            world.playSound(currentLoc, Sound.WEATHER_RAIN, 1.5f, 1.0f);
                        }
                    }.runTaskLater(plugin, 5L);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, MOVE_DELAY);

        player.sendMessage(ChatColor.AQUA + "§8[§bTide§8] You have summoned a massive tidal wall!");
        cooldownManager.setCooldown(player, TrimPattern.TIDE, 60000); // 2 minute cooldown
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            TidePrimary(player);
        }
    }
}