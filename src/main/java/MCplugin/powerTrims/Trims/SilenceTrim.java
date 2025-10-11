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
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SilenceTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    // --- STATE & CONSTANTS ---
    private final Map<UUID, Long> wardensEchoCooldowns = new HashMap<>();

    private final long WARDENS_ECHO_COOLDOWN_MS;
    private final double PRIMARY_RADIUS;
    private final int POTION_DURATION_TICKS;
    private final int PEARL_COOLDOWN_TICKS;
    private final double ECHO_RADIUS;
    private final int ECHO_EFFECT_DURATION_TICKS;
    private final int MAX_AFFECTED_ENTITIES;
    private final long PRIMARY_COOLDOWN;

    public SilenceTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        // Read values from the now-centralized config
        WARDENS_ECHO_COOLDOWN_MS = configManager.getLong("silence.passive.cooldown");
        PRIMARY_RADIUS = configManager.getDouble("silence.primary.radius");
        POTION_DURATION_TICKS = configManager.getInt("silence.primary.potion_duration_ticks");
        PEARL_COOLDOWN_TICKS = configManager.getInt("silence.primary.pearl_cooldown_ticks");
        ECHO_RADIUS = configManager.getDouble("silence.passive.echo_radius");
        ECHO_EFFECT_DURATION_TICKS = configManager.getInt("silence.passive.effect_duration_ticks");
        MAX_AFFECTED_ENTITIES = configManager.getInt("silence.primary.max_affected_entities");
        PRIMARY_COOLDOWN = configManager.getLong("silence.primary.cooldown");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        abilityManager.registerPrimaryAbility(TrimPattern.SILENCE, this::activateSilencePrimary);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateSilencePrimary(Player player) {
        if (!configManager.isTrimEnabled("silence")) return;

        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE) ||
                cooldownManager.isOnCooldown(player, TrimPattern.SILENCE)) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        Location playerLocation = player.getLocation();

        createWardenShockwave(playerLocation, PRIMARY_RADIUS);

        player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_ANGRY, 2.0f, 1.5f);
        player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 1.5f);

        int affectedCount = 0;
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(playerLocation, PRIMARY_RADIUS)) {
            if (affectedCount >= MAX_AFFECTED_ENTITIES) break;
            if (target.equals(player)) continue;

            if (target instanceof Player targetPlayer) {
                if (trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue;
                }
                targetPlayer.setCooldown(Material.ENDER_PEARL, PEARL_COOLDOWN_TICKS);
                sendMessages(targetPlayer);
            }

            applyPrimaryEffects(target);
            affectedCount++;
        }

        Messaging.sendTrimMessage(player, "Silence", ChatColor.RED, "You have unleashed the Warden's Roar!");
        cooldownManager.setCooldown(player, TrimPattern.SILENCE, PRIMARY_COOLDOWN);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double healthAfterDamage = player.getHealth() - event.getFinalDamage();
        if (healthAfterDamage > 8.0) return;

        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE)) return;

        if (wardensEchoCooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        activateWardensEcho(player);
    }

    private void activateWardensEcho(Player player) {
        Location playerLocation = player.getLocation();
        player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
        triggerWardensEchoParticles(player);

        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(playerLocation, ECHO_RADIUS)) {
            if (target.equals(player)) continue;

            if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                continue;
            }
            applyEchoEffects(player, target);
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ECHO_EFFECT_DURATION_TICKS, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ECHO_EFFECT_DURATION_TICKS, 1));
        Messaging.sendTrimMessage(player, "Silence", ChatColor.RED, "Your armor has unleashed " + ChatColor.BOLD + "Warden's Echo!");

        wardensEchoCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + WARDENS_ECHO_COOLDOWN_MS);
    }

    private void applyPrimaryEffects(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, POTION_DURATION_TICKS, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, POTION_DURATION_TICKS, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, POTION_DURATION_TICKS, 1));
    }

    private void applyEchoEffects(Player player, LivingEntity target) {
        Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.2);
        knockback.setY(0.5);
        target.setVelocity(knockback);

        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ECHO_EFFECT_DURATION_TICKS, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ECHO_EFFECT_DURATION_TICKS, 1));

        if (target instanceof Player playerTarget) {
            Messaging.sendTrimMessage(playerTarget, "Silence", ChatColor.RED, "You were hit by " + ChatColor.BOLD + "Warden's Echo!");
        }
    }

    private void sendMessages(Player targetPlayer) {
        Messaging.sendTrimMessage(targetPlayer, "Silence", ChatColor.RED, "You have been hit with the " + ChatColor.BOLD + "Warden's Roar!");
        Messaging.sendTrimMessage(targetPlayer, "Silence", ChatColor.RED, "Your Ender Pearl is on cooldown!");
    }

    private void createWardenShockwave(Location center, double maxRadius) {
        new BukkitRunnable() {
            double currentRadius = 1.0;

            @Override
            public void run() {
                if (currentRadius > maxRadius) {
                    this.cancel();
                    center.getWorld().spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1, 0), 1);
                    return;
                }

                World world = center.getWorld();
                int points = (int) (currentRadius * 8);

                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    double x = center.getX() + (currentRadius * Math.cos(angle));
                    double z = center.getZ() + (currentRadius * Math.sin(angle));
                    Location particleLoc = new Location(world, x, center.getY() + 0.5, z);

                    world.spawnParticle(Particle.SCULK_SOUL, particleLoc, 1, 0, 0, 0, 0);
                }

                currentRadius += 1.0;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void triggerWardensEchoParticles(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            double x = ECHO_RADIUS * Math.cos(angle);
            double z = ECHO_RADIUS * Math.sin(angle);
            world.spawnParticle(Particle.SCULK_CHARGE_POP, loc.clone().add(x, 1, z), 5, 0.2, 0.5, 0.2, 0.1);
        }

        world.spawnParticle(Particle.SONIC_BOOM, loc.add(0, 1, 0), 3);
        world.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 2.5f, 0.5f);
    }
}
