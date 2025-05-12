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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SnoutTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; // Add an instance of the Trust Manager
    private final NamespacedKey effectKey;
    private static final long ROAR_COOLDOWN = 120000; // 2 minutes
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final Map<UUID, LivingEntity> summonerTargetMap = new ConcurrentHashMap<>();
    private final List<WitherSkeleton> summonedSkeletons = Collections.synchronizedList(new ArrayList<>());
    private final NamespacedKey summonerKey;
    private final Scoreboard snoutScoreboard; // Plugin's private scoreboard
    private Team necromancerTeam;


    public SnoutTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; // Initialize the Trust Manager
        this.summonerKey = new NamespacedKey(plugin, "summoner");
        this.effectKey = new NamespacedKey(plugin, "snout_trim_effect");
        this.snoutScoreboard = Bukkit.getScoreboardManager().getNewScoreboard(); // Initialize the plugin's scoreboard
        initializeTeam();
        startSkeletonTargetUpdater();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startSnoutPassive();
    }

    private void initializeTeam() {
        necromancerTeam = snoutScoreboard.registerNewTeam("necromancer_team"); // Use the plugin's scoreboard
        if (necromancerTeam != null) {
            necromancerTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            necromancerTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            necromancerTeam.setCanSeeFriendlyInvisibles(true);
        }
    }

    private void startSnoutPassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean hasSnoutTrim = ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SNOUT);
                boolean hasEffect = player.hasPotionEffect(PotionEffectType.STRENGTH);
                boolean stored = player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE);

                if (hasSnoutTrim && !hasEffect) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, true, false, true));
                    player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                } else if (!hasSnoutTrim && stored) {
                    player.removePotionEffect(PotionEffectType.STRENGTH);
                    player.getPersistentDataContainer().remove(effectKey);
                }
            }
        }, 0L, 20L);
    }


    public void SnoutPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SNOUT)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.SNOUT)) return;

        Location center = player.getLocation();
        double radius = 2.5;
        NamespacedKey minionKey = new NamespacedKey(plugin, "necromancer_minion");

        for (int i = 0; i < 5; i++) {
            double angle = 2 * Math.PI * i / 5;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location spawnLoc = new Location(center.getWorld(), x, center.getY(), z);

            WitherSkeleton skeleton = player.getWorld().spawn(spawnLoc, WitherSkeleton.class, skel -> {
                skel.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
                skel.getPersistentDataContainer().set(summonerKey, PersistentDataType.STRING, player.getUniqueId().toString());

                skel.setCustomName(LEGACY.serialize(Component.text("Necromancer's Minion", NamedTextColor.DARK_GRAY)));
                skel.setCustomNameVisible(true);
                skel.setHealth(20.0);


                skel.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 99999, 1, false, false));
                skel.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                skel.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 99999, 1, false, false));

                ItemStack weapon = new ItemStack(Material.NETHERITE_SWORD);
                weapon.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
                skel.getEquipment().setItemInMainHand(weapon);
                skel.getEquipment().setItemInMainHandDropChance(0.0f);
            });

            player.getWorld().spawnParticle(Particle.SOUL, skeleton.getLocation(), 30, 0.5, 1, 0.5, 0.1);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, skeleton.getLocation(), 20, 0.3, 0.5, 0.3, 0.05);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);

            assignToTeam(player, skeleton);
            summonedSkeletons.add(skeleton);
        }

        cooldownManager.setCooldown(player, TrimPattern.SNOUT, ROAR_COOLDOWN);
        player.sendMessage(Component.text("[Snout] You summoned Minions!", NamedTextColor.RED));
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            SnoutPrimary(player);
        }
    }


    // Update skeletons’ targets more responsively
    private void startSkeletonTargetUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                synchronized (summonedSkeletons) {
                    Iterator<WitherSkeleton> iter = summonedSkeletons.iterator();
                    while (iter.hasNext()) {
                        WitherSkeleton skeleton = iter.next();
                        // Remove dead skeletons
                        if (skeleton.isDead() || !skeleton.isValid()) {
                            iter.remove();
                            continue;
                        }
                        // Get summoner from skeleton data
                        if (!skeleton.getPersistentDataContainer().has(summonerKey, PersistentDataType.STRING))
                            continue;
                        UUID summonerUUID = UUID.fromString(
                                Objects.requireNonNull(skeleton.getPersistentDataContainer().get(summonerKey, PersistentDataType.STRING)));
                        Player summoner = Bukkit.getPlayer(summonerUUID);
                        if (summoner == null) continue;
                        // Do not target the summoner or trusted players
                        LivingEntity currentTarget = skeleton.getTarget();
                        LivingEntity preferredTarget = summonerTargetMap.get(summonerUUID);
                        if (preferredTarget != null && !preferredTarget.equals(summoner)) {
                            if (preferredTarget instanceof Player) {
                                // Check if the preferred target is trusted by the summoner
                                if (trustManager.isTrusted(summonerUUID, preferredTarget.getUniqueId())) {
                                    // If trusted, do nothing (don't target)
                                } else {
                                    // Update target if not already targeting it
                                    if (!preferredTarget.equals(currentTarget)) {
                                        skeleton.setTarget(preferredTarget);
                                    }
                                }
                            } else {
                                // If the preferred target is not a player, target it
                                if (!preferredTarget.equals(currentTarget)) {
                                    skeleton.setTarget(preferredTarget);
                                }
                            }
                        } else if (currentTarget != null && currentTarget instanceof Player) {
                            // If currently targeting a player, check if they are trusted
                            if (trustManager.isTrusted(summonerUUID, currentTarget.getUniqueId())) {
                                skeleton.setTarget(null); // Stop targeting trusted players
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // Run every 5 ticks
    }

    // Prevent skeletons from targeting the summoner or trusted players when they first choose a target
    @EventHandler
    public void onSkeletonTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton skeleton)) return;
        LivingEntity target = event.getTarget();
        if (target == null) return;
        if (skeleton.getPersistentDataContainer().has(summonerKey, PersistentDataType.STRING)) {
            UUID summonerUUID = UUID.fromString(Objects.requireNonNull(skeleton.getPersistentDataContainer().get(summonerKey, PersistentDataType.STRING)));
            Player summoner = Bukkit.getPlayer(summonerUUID);
            if (summoner == null) return;
            if (target.equals(summoner) || (target instanceof Player && trustManager.isTrusted(summonerUUID, target.getUniqueId()))) {
                event.setCancelled(true);
            }
        }
    }

    private void assignToTeam(Player player, WitherSkeleton skeleton) {
        if (necromancerTeam != null) {
            necromancerTeam.addEntry(player.getName());
            necromancerTeam.addEntry(skeleton.getUniqueId().toString());
        }
        skeleton.addScoreboardTag("necromancer_minion_" + player.getUniqueId());
    }

    // When the summoner attacks an entity, record that entity as the preferred target.
    @EventHandler
    public void onSummonerAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (damager instanceof Player && victim instanceof LivingEntity) {
            Player summoner = (Player) damager;
            // If the victim is a WitherSkeleton and belongs to the summoner, skip it.
            if (victim instanceof WitherSkeleton) {
                WitherSkeleton ws = (WitherSkeleton) victim;
                if (ws.getScoreboardTags().contains("necromancer_minion_" + summoner.getUniqueId())) {
                    return;
                }
            }
            summonerTargetMap.put(summoner.getUniqueId(), (LivingEntity) victim);
        }
    }
}