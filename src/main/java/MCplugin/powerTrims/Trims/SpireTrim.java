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

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SpireTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final AbilityManager abilityManager;
    private final Set<UUID> markedTargets;
    private final Set<UUID> dashingPlayers;
    private final ConfigManager configManager;

    // --- CONSTANTS ---
    private final double DASH_DISTANCE;
    private final double DASH_SPEED;
    private final double KNOCKBACK_STRENGTH;
    private final int SLOW_DURATION;
    private final int VULNERABLE_DURATION;
    private final double DAMAGE_AMPLIFICATION;
    private final long ABILITY_COOLDOWN;

    public SpireTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;
        this.markedTargets = new HashSet<>();
        this.dashingPlayers = new HashSet<>();

        // Load values from config
        DASH_DISTANCE = configManager.getDouble("spire.primary.dash_distance");
        DASH_SPEED = configManager.getDouble("spire.primary.dash_speed");
        KNOCKBACK_STRENGTH = configManager.getDouble("spire.primary.knockback_strength");
        SLOW_DURATION = configManager.getInt("spire.primary.slow_duration");
        VULNERABLE_DURATION = configManager.getInt("spire.primary.vulnerable_duration");
        DAMAGE_AMPLIFICATION = configManager.getDouble("spire.primary.damage_amplification");
        ABILITY_COOLDOWN = configManager.getLong("spire.primary.cooldown");

        abilityManager.registerPrimaryAbility(TrimPattern.SPIRE, this::SpirePrimary);
    }


    public void SpirePrimary(Player player) {
        if (!configManager.isTrimEnabled("spire")) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SPIRE)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.SPIRE)) return;

        Location startLoc = player.getLocation();
        Vector direction = player.getLocation().getDirection().normalize();

        player.getWorld().playSound(startLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        player.getWorld().playSound(startLoc, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 1.5f);
        player.getWorld().playSound(startLoc, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.2f);

        createDashEffect(startLoc);

        UUID playerId = player.getUniqueId();
        dashingPlayers.add(playerId);
        player.setInvulnerable(true);
        player.setVelocity(direction.multiply(DASH_SPEED));

        new BukkitRunnable() {
            private double distanceTraveled = 0;
            private Location lastLoc = startLoc.clone();
            private int ticksElapsed = 0;
            private final List<Entity> hitEntities = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || ticksElapsed > 20) {
                    endDash();
                    return;
                }

                Location currentLoc = player.getLocation();
                double distanceThisTick = lastLoc.distance(currentLoc);
                distanceTraveled += distanceThisTick;

                createDashTrail(lastLoc, currentLoc);

                for (Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity && entity != player && !hitEntities.contains(entity)) {
                        LivingEntity target = (LivingEntity) entity;
                        if (target instanceof Player targetPlayer) {
                            // Check if the target player is trusted by the dashing player
                            if (trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                                continue; // Skip trusted players
                            }
                        }
                        hitEntities.add(target);
                        handleEntityCollision(target, direction, player); // Pass the player as well
                    }
                }

                if (distanceTraveled < DASH_DISTANCE && player.isOnGround()) {
                    Vector currentVel = player.getVelocity();
                    Vector horizontalVel = direction.multiply(DASH_SPEED);
                    horizontalVel.setY(currentVel.getY());
                    player.setVelocity(horizontalVel);
                }

                if (distanceTraveled >= DASH_DISTANCE || player.isOnGround()) {
                    endDash();
                    return;
                }

                lastLoc = currentLoc;
                ticksElapsed++;
            }

            private void endDash() {
                dashingPlayers.remove(playerId);
                player.setInvulnerable(false);
                cooldownManager.setCooldown(player, TrimPattern.SPIRE, ABILITY_COOLDOWN);
                Messaging.sendTrimMessage(player, "Spire", ChatColor.GREEN, "You used " + ChatColor.GOLD + "Spire Dash" + ChatColor.GREEN + "!");
                this.cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createDashEffect(Location location) {
        World world = location.getWorld();
        world.spawnParticle(Particle.FLASH, location, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.CLOUD, location, 8, 0.3, 0.2, 0.3, 0);
        world.spawnParticle(Particle.END_ROD, location, 3, 0.2, 0.2, 0.2, 0);
    }

    private void createDashTrail(Location from, Location to) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        for (double d = 0; d < distance; d += 1.0) {
            Location particleLoc = from.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0.1, 0.1, 0.1, 0);
            if (Math.random() < 0.3) {
                world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    private void handleEntityCollision(LivingEntity target, Vector dashDirection, Player damager) {
        if (target instanceof Player targetPlayer) {
            if (trustManager.isTrusted(damager.getUniqueId(), targetPlayer.getUniqueId())) {
                return; // Do nothing if the target is trusted
            }
        }
        // Knockback effect
        Vector knockbackVec = dashDirection.clone().multiply(KNOCKBACK_STRENGTH);
        knockbackVec.setY(Math.max(0.2, knockbackVec.getY()));
        target.setVelocity(knockbackVec);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_DURATION, 1, true, true));

        // Mark the target to track it for damage amplification
        UUID targetId = target.getUniqueId();
        markedTargets.add(targetId);

        // Apply the glowing effect to the target
        target.setGlowing(true);

        // Particle effects on collision
        Location hitLoc = target.getLocation();
        World world = target.getWorld();
        world.spawnParticle(Particle.CLOUD, hitLoc.add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.2);
        world.spawnParticle(Particle.SNOWFLAKE, hitLoc, 8, 0.2, 0.2, 0.2, 0.1);
        world.spawnParticle(Particle.EXPLOSION, hitLoc, 2, 0.1, 0.1, 0.1, 0);
        world.spawnParticle(Particle.END_ROD, hitLoc, 5, 0.2, 0.2, 0.2, 0.1);
        world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
        world.playSound(hitLoc, Sound.ENTITY_IRON_GOLEM_HURT, 0.5f, 1.2f);
        world.playSound(hitLoc, Sound.BLOCK_GLASS_BREAK, 0.3f, 2.0f);

        // Mark the target for a limited time
        new BukkitRunnable() {
            @Override
            public void run() {
                markedTargets.remove(targetId);
                target.setGlowing(false); // Remove the glowing effect after a certain time
            }
        }.runTaskLater(plugin, VULNERABLE_DURATION);

        if (target instanceof Player) {
            Messaging.sendTrimMessage((Player) target, "Spire", ChatColor.RED, "You've been marked by a Spire Dash!");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // If the target has been marked, apply damage amplification
        if (markedTargets.contains(target.getUniqueId())) {
            // Check if the target is trusted (even though they were marked).
            // This is a safety measure in case of timing issues.
            if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                return; // Don't amplify damage against trusted players
            }
            event.setDamage(event.getDamage() * (1 + DAMAGE_AMPLIFICATION)); // Amplify damage
            markedTargets.remove(target.getUniqueId());

            // Particle effects and sound for the amplified damage
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.2);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
        }
    }

    @EventHandler
    public void onEntityFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && dashingPlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (!configManager.isTrimEnabled("spire")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }
}
