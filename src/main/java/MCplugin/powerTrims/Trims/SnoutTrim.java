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
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SnoutTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    // --- CONSTANTS ---
    private final long ROAR_COOLDOWN;
    private final long MINION_LIFESPAN_TICKS;
    private static final NamespacedKey SUMMONER_KEY;

    // Static initializer for the NamespacedKey
    static {
        // It's good practice to initialize keys once to avoid typos.
        SUMMONER_KEY = new NamespacedKey("powertrims", "snout_summoner_uuid");
    }

    // --- STATE MANAGEMENT ---
    // Map a player's UUID to their personal list of minions.
    private final Map<UUID, List<WitherSkeleton>> playerMinions = new HashMap<>();

    public SnoutTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        ROAR_COOLDOWN = configManager.getLong("snout.primary.cooldown");
        MINION_LIFESPAN_TICKS = configManager.getLong("snout.primary.minion_lifespan_ticks");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        abilityManager.registerPrimaryAbility(TrimPattern.SNOUT, this::activateSnoutPrimary);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
           abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateSnoutPrimary(Player player) {
        if (!configManager.isTrimEnabled("snout")) {
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SNOUT) || cooldownManager.isOnCooldown(player, TrimPattern.SNOUT)) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        Location center = player.getLocation();
        double radius = 2.5;

        // Ensure the player has a minion list initialized
        playerMinions.putIfAbsent(player.getUniqueId(), new ArrayList<>());

        for (int i = 0; i < 5; i++) {
            double angle = 2 * Math.PI * i / 5;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location spawnLoc = new Location(center.getWorld(), x, center.getY(), z);

            WitherSkeleton skeleton = player.getWorld().spawn(spawnLoc, WitherSkeleton.class, skel -> {
                // Store the owner's UUID in the skeleton's data
                skel.getPersistentDataContainer().set(SUMMONER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());

                // Set appearance and stats
                skel.setCustomName(ChatColor.DARK_GRAY + "Necromancer's Minion");
                skel.setCustomNameVisible(true);
                skel.setHealth(20.0);

                // **OPTIMIZATION**: Use the simpler setCollidable instead of scoreboard teams
                skel.setCollidable(false);

                // Apply buffs
                skel.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1, false, false));
                skel.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                skel.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));

                // Give weapon
                ItemStack weapon = new ItemStack(Material.STONE_SWORD);
                Objects.requireNonNull(skel.getEquipment()).setItemInMainHand(weapon);
                skel.getEquipment().setItemInMainHandDropChance(0.0f);
            });

            // Add to the owner's personal minion list
            playerMinions.get(player.getUniqueId()).add(skeleton);

            // Add a timed lifespan for automatic cleanup
            scheduleMinionRemoval(skeleton, MINION_LIFESPAN_TICKS);

            // Visual and sound effects
            player.getWorld().spawnParticle(Particle.SOUL, skeleton.getLocation(), 30, 0.5, 1, 0.5, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);
        }

        cooldownManager.setCooldown(player, TrimPattern.SNOUT, ROAR_COOLDOWN);
        Messaging.sendTrimMessage(player, "Snout", ChatColor.DARK_RED, "You have summoned your Minions!");
    }

    /**
     * This single event handler now controls minion targeting.
     * It makes minions attack what their owner attacks (offensive)
     * and attack whatever hurts their owner (defensive).
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player owner = null;
        LivingEntity target = null;

        // Case 1: Offensive - The owner is the one dealing damage
        if (event.getDamager() instanceof Player) {
            owner = (Player) event.getDamager();
            target = (LivingEntity) event.getEntity();
        }
        // Case 2: Defensive - The owner is the one being damaged
        else if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            owner = (Player) event.getEntity();
            target = (LivingEntity) event.getDamager();
        }

        // If an owner and target were identified, command the minions
        if (owner != null && target != null) {
            List<WitherSkeleton> minions = playerMinions.get(owner.getUniqueId());
            if (minions != null && !minions.isEmpty() && isValidTarget(target, owner)) {
                // Command every minion to attack the new target
                for (WitherSkeleton minion : minions) {
                    if (minion.isValid()) {
                        minion.setTarget(target);
                    }
                }
            }
        }
    }

    /**
     * Prevents minions from choosing their owner or a trusted player as a target.
     */
    @EventHandler
    public void onMinionInitialTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton minion) || event.getTarget() == null) return;

        String ownerUUIDString = minion.getPersistentDataContainer().get(SUMMONER_KEY, PersistentDataType.STRING);
        if (ownerUUIDString == null) return;

        Player owner = Bukkit.getPlayer(UUID.fromString(ownerUUIDString));
        if (owner != null && !isValidTarget(event.getTarget(), owner)) {
            event.setCancelled(true);
        }
    }

    /**
     * MEMORY LEAK FIX: Cleans up when a minion dies.
     */
    @EventHandler
    public void onMinionDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton minion)) return;

        String ownerUUIDString = minion.getPersistentDataContainer().get(SUMMONER_KEY, PersistentDataType.STRING);
        if (ownerUUIDString != null) {
            UUID ownerUUID = UUID.fromString(ownerUUIDString);
            List<WitherSkeleton> minions = playerMinions.get(ownerUUID);
            if (minions != null) {
                minions.remove(minion);
            }
        }
    }

    /**
     * MEMORY LEAK FIX: Cleans up when a player logs out.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        List<WitherSkeleton> minions = playerMinions.remove(playerUUID);
        if (minions != null) {
            for (WitherSkeleton minion : minions) {
                if (minion.isValid()) {
                    minion.remove();
                }
            }
        }
    }

    // --- HELPER METHODS ---

    private void scheduleMinionRemoval(WitherSkeleton minion, long ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (minion.isValid()) {
                    minion.getWorld().spawnParticle(Particle.SMOKE, minion.getLocation().add(0, 1, 0), 20, 0.2, 0.5, 0.2, 0.05);
                    minion.remove(); // This will trigger the EntityDeathEvent for cleanup from the list
                }
            }
        }.runTaskLater(plugin, ticks);
    }

    private boolean isValidTarget(Entity target, Player owner) {
        if (target == null || !target.isValid() || target.equals(owner)) {
            return false;
        }

        // Don't target trusted players
        if (target instanceof Player && trustManager.isTrusted(owner.getUniqueId(), target.getUniqueId())) {
            return false;
        }

        // Don't target other minions of the same owner
        String targetOwnerUUID = target.getPersistentDataContainer().get(SUMMONER_KEY, PersistentDataType.STRING);
        return !(targetOwnerUUID != null && targetOwnerUUID.equals(owner.getUniqueId().toString()));
    }
}
