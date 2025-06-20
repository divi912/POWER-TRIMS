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
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HostTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final NamespacedKey effectKey;
    private static final long ESSENCE_REAPER_COOLDOWN = 120000; // 2 minutes cooldown
    private static final double EFFECT_STEAL_RADIUS = 10.0;
    private static final double HEALTH_STEAL_AMOUNT = 4.0; // 2 hearts
    private final Map<Player, Set<PotionEffectType>> stolenEffects = new ConcurrentHashMap<>();
    private final Map<PotionEffectType, Player> effectOwners = new ConcurrentHashMap<>();


    public HostTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
            this.plugin = plugin;
            this.cooldownManager = cooldownManager;
            this.trustManager = trustManager;
            this.effectKey = new NamespacedKey(plugin, "host_trim_effect");
            Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player targetPlayer)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) event.getEntity();
        if (!(attacker instanceof Mob mob)) {
            return;
        }

        // Check if the target player is wearing full Host Trim armor
        if (ArmourChecking.hasFullTrimmedArmor(targetPlayer, TrimPattern.HOST)) {
            EntityType attackerType = attacker.getType();

            // Check if the attacker is NOT a boss mob
            if (attackerType != EntityType.WARDEN &&
                    attackerType != EntityType.ENDER_DRAGON &&
                    attackerType != EntityType.WITHER) {
                event.setCancelled(true); // Cancel the targeting
                mob.setTarget(null); // Optionally, clear the mob's current target
            }
        }
    }


    public void HostPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.HOST)) {
            return;
        }
        if (cooldownManager.isOnCooldown(player, TrimPattern.HOST)) {
            return;
        }

        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        Player hostUser = player; // Store the player using the ability

        // Initialize the set of stolen effects if it's not already initialized
        stolenEffects.putIfAbsent(player, new HashSet<>());

        // For each nearby player, steal positive potion effects and health
        for (Entity entity : world.getNearbyEntities(playerLoc, EFFECT_STEAL_RADIUS, EFFECT_STEAL_RADIUS, EFFECT_STEAL_RADIUS)) {
            if (entity instanceof Player targetPlayer && !targetPlayer.equals(hostUser)) {
                if (trustManager.isTrusted(hostUser.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue; // Skip trusted players
                }
                Collection<PotionEffect> effects = targetPlayer.getActivePotionEffects();
                for (PotionEffect effect : effects) {
                    PotionEffectType type = effect.getType();
                    if (isPositiveEffect(type) && !stolenEffects.get(player).contains(type)) {
                        // Prevent the player from stealing back an effect they had stolen from them
                        if (effectOwners.containsKey(type) && effectOwners.get(type).equals(hostUser)) {
                            continue; // Don't allow stealing back their own effect
                        }

                        targetPlayer.removePotionEffect(type); // Remove from target
                        // Add the effect to the user with an increased amplifier (+1 level)
                        player.addPotionEffect(new PotionEffect(type, effect.getDuration(), effect.getAmplifier() + 1));

                        // Track the stolen effect
                        stolenEffects.get(player).add(type);
                        effectOwners.put(type, targetPlayer); // Track who owned this effect
                    }
                }

                // Steal health: take up to 3 hearts from the target and add it to the user
                double stolenHealth = Math.min(targetPlayer.getHealth(), HEALTH_STEAL_AMOUNT);
                targetPlayer.setHealth(Math.max(0, targetPlayer.getHealth() - stolenHealth));
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + stolenHealth));

                // Create a particle trail from the user to the target for visual effect
                createParticleTrail(player.getLocation(), targetPlayer.getLocation(), world);
            }
        }

        world.playSound(playerLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        world.spawnParticle(Particle.SOUL, playerLoc, 30, 1, 1, 1, 0.1);

        cooldownManager.setCooldown(player, TrimPattern.HOST, ESSENCE_REAPER_COOLDOWN);
        player.sendMessage(ChatColor.DARK_PURPLE + "Essence Reaper activated!");
    }

    // Helper method to determine if a potion effect is positive
    private boolean isPositiveEffect(PotionEffectType type) {
        return type == PotionEffectType.SPEED
                || type == PotionEffectType.REGENERATION
                || type == PotionEffectType.STRENGTH
                || type == PotionEffectType.FIRE_RESISTANCE
                || type == PotionEffectType.RESISTANCE
                || type == PotionEffectType.ABSORPTION;
    }

    // Creates a particle trail from start to end
    private void createParticleTrail(Location start, Location end, World world) {
        Vector diff = end.toVector().subtract(start.toVector());
        double length = diff.length();
        int steps = (int) (length * 4);
        Vector step = diff.clone().normalize().multiply(length / steps);
        Location point = start.clone();
        for (int i = 0; i < steps; i++) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0, 0, 0, 0);
            point.add(step);
        }
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            HostPrimary(player);
        }
    }


}