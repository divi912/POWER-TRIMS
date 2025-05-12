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
import MCplugin.powerTrims.Logic.PersistentTrustManager; // Import the Trust Manager
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SilenceTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final NamespacedKey effectKey;
    private final Map<UUID, AtomicLong> wardensEchoCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> effectCooldowns = new ConcurrentHashMap<>();

    private static final long WARDENS_ECHO_COOLDOWN = 120000; // 2 minutes
    private static final long EFFECT_COOLDOWN = 1000; // 1 second
    private static final double PRIMARY_RADIUS = 15.0;
    private static final int POTION_DURATION = 300; // 10 seconds
    private static final int PEARL_COOLDOWN = 200; // 10 seconds
    private static final double ECHO_RADIUS = 6.0;
    private static final int ECHO_EFFECT_DURATION = 60; // 3 seconds
    private static final int MAX_AFFECTED_ENTITIES = 30;

    public SilenceTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.effectKey = new NamespacedKey(plugin, "silence_trim_effect");
        SilencePassive();
    }

    private void SilencePassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE)) {
                    if (!player.hasPotionEffect(PotionEffectType.STRENGTH)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, true, false, true));
                        player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 3);
                    }
                } else if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                    player.removePotionEffect(PotionEffectType.STRENGTH);
                    player.getPersistentDataContainer().remove(effectKey);
                }
            }
        }, 0L, 20L);
    }

    public void SilencePrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE) ||
                cooldownManager.isOnCooldown(player, TrimPattern.SILENCE)) return;

        Location playerLocation = player.getLocation();
        createParticleCircle(player, playerLocation, PRIMARY_RADIUS, 20);
        Player silenceUser = player; // Store the player using the ability

        if (!isOnEffectCooldown(player)) {
            player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_ANGRY, 2.0f, 1.5f);
            player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 2.0f, 1.5f);
            player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 1.5f);
            player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_AGITATED, 2.0f, 1.5f);
            setEffectCooldown(player);
        }

        int affectedCount = 0;
        for (Entity entity : player.getWorld().getNearbyEntities(playerLocation, PRIMARY_RADIUS, PRIMARY_RADIUS, PRIMARY_RADIUS)) {
            if (affectedCount >= MAX_AFFECTED_ENTITIES) break;

            if (entity instanceof Player target && !target.equals(silenceUser)) {
                if (trustManager.isTrusted(silenceUser.getUniqueId(), target.getUniqueId())) {
                    continue; // Skip trusted players
                }
                target.setCooldown(Material.ENDER_PEARL, PEARL_COOLDOWN);
                affectedCount++;
            }

            if (entity instanceof LivingEntity target && !target.equals(silenceUser)) {
                if (target instanceof Player targetPlayer && trustManager.isTrusted(silenceUser.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue; // Skip trusted players
                }
                applyEffects(target);
                sendMessages(target);
                affectedCount++;
            }
        }

        player.sendMessage("§8[§cSilence§8] §7You have activated the §cSilence ability!");
        cooldownManager.setCooldown(player, TrimPattern.SILENCE, 90000); // 1.5 min cooldown
    }

    private void applyEffects(LivingEntity target) {
        target.removePotionEffect(PotionEffectType.BLINDNESS); // Refresh blindness
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, POTION_DURATION, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, POTION_DURATION, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, POTION_DURATION, 1, false, true, true));
    }

    private void sendMessages(LivingEntity target) {
        if (target instanceof Player playerTarget) {
            playerTarget.sendMessage("§8[§cSilence§8] §7You have been hit with " + ChatColor.RED + ChatColor.BOLD + "Warden Roar!");
            playerTarget.sendMessage("§8[§cSilence§8] §7Your Ender Pearl is locked for §c10 seconds!");
            playerTarget.sendMessage("§8[§cSilence§8] §7You are affected by §cBlindness §7and §cSlowness §7for §c10 seconds!");
        }
    }

    private boolean isOnEffectCooldown(Player player) {
        AtomicLong lastEffect = effectCooldowns.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        long currentTime = System.currentTimeMillis();
        return currentTime < lastEffect.get();
    }

    private void setEffectCooldown(Player player) {
        AtomicLong cooldown = effectCooldowns.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        cooldown.set(System.currentTimeMillis() + EFFECT_COOLDOWN);
    }

    private boolean isOnWardensEchoCooldown(Player player) {
        AtomicLong cooldown = wardensEchoCooldowns.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        return System.currentTimeMillis() < cooldown.get();
    }

    private void setWardensEchoCooldown(Player player) {
        AtomicLong cooldown = wardensEchoCooldowns.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        cooldown.set(System.currentTimeMillis() + WARDENS_ECHO_COOLDOWN);
    }

    private void createParticleCircle(Player player, Location center, double radius, int durationTicks) {
        if (isOnEffectCooldown(player)) {
            return;
        }
        setEffectCooldown(player);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= durationTicks) {
                    cancel();
                    return;
                }

                int points = Math.min(180, (int)(radius * 12));
                for (int i = 0; i < points; i++) {
                    double angle = Math.toRadians(i * (360.0 / points));
                    double x = center.getX() + (radius * Math.cos(angle));
                    double z = center.getZ() + (radius * Math.sin(angle));
                    Location particleLocation = new Location(center.getWorld(), x, center.getY() + 0.5, z);

                    center.getWorld().spawnParticle(Particle.LARGE_SMOKE, particleLocation, 2, 0, 0, 0, 0);
                    center.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, particleLocation, 7, 0.1, 0.1, 0.1, 0.1);
                    center.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLocation, 3, 0.1, 0.1, 0.1, 0.05);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            SilencePrimary(player);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SILENCE)) return;
        if (isOnWardensEchoCooldown(player)) return;

        double newHealth = player.getHealth() - event.getFinalDamage();
        if (newHealth > 8) return;

        activateWardensEcho(player);
    }

    private void activateWardensEcho(Player player) {
        Location playerLocation = player.getLocation();
        player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
        triggerWardensEcho(player);
        Player silenceUser = player; // Store the player using the ability

        for (Entity entity : player.getWorld().getNearbyEntities(playerLocation, ECHO_RADIUS, ECHO_RADIUS, ECHO_RADIUS)) {
            if (entity instanceof LivingEntity target && !target.equals(silenceUser)) {
                if (target instanceof Player targetPlayer && trustManager.isTrusted(silenceUser.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue; // Skip trusted players
                }
                applyEchoEffects(player, target);
            }
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ECHO_EFFECT_DURATION, 1, false, false, true));
        player.sendMessage("§8[§cSilence§8] §7Your armor has unleashed " + ChatColor.BOLD + "§cWarden's Echo!");
        setWardensEchoCooldown(player);
    }

    private void applyEchoEffects(Player player, LivingEntity target) {
        Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.2);
        knockback.setY(0.5);
        target.setVelocity(knockback);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ECHO_EFFECT_DURATION, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ECHO_EFFECT_DURATION, 1, false, true, true));

        if (target instanceof Player) {
            ((Player) target).sendMessage("§8[§cSilence§8] §7You were hit by " + ChatColor.BOLD + "§cWarden's Echo!");
        }
    }

    private void triggerWardensEcho(Player player) {
        Location playerLocation = player.getLocation();
        for (int i = 0; i < 360; i += 8) {
            for (double y = 0; y <= 2; y += 0.4) {
                double angle = Math.toRadians(i);
                double x = ECHO_RADIUS * Math.cos(angle);
                double z = ECHO_RADIUS * Math.sin(angle);
                Location particleLoc = playerLocation.clone().add(x, y, z);

                player.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, particleLoc, 12, 0.1, 0.1, 0.1, 0.1);
                player.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 6, 0.1, 0.1, 0.1, 0.05);
            }
        }

        player.getWorld().spawnParticle(Particle.SONIC_BOOM, playerLocation.add(0, 1, 0), 5);
        player.getWorld().playSound(playerLocation, Sound.ENTITY_WARDEN_HEARTBEAT, 2.5f, 0.5f);
    }
}