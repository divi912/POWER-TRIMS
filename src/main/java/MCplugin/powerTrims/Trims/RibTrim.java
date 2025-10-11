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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RibTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    // --- CONSTANTS ---
    private final long RIB_COOLDOWN;
    private final long MINION_LIFESPAN_TICKS;
    private static final NamespacedKey OWNER_KEY;

    // Static initializer for the NamespacedKey
    static {
        // It's good practice to initialize keys once.
        OWNER_KEY = new NamespacedKey("powertrims", "owner_uuid");
    }

    // --- INSTANCE VARIABLES (non-static to prevent memory leaks across reloads) ---
    private final Map<UUID, LivingEntity> playerTargetMap = new HashMap<>();
    private final Map<UUID, List<Mob>> playerMinionMap = new HashMap<>();

    public RibTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        RIB_COOLDOWN = configManager.getLong("rib.primary.cooldown");
        MINION_LIFESPAN_TICKS = configManager.getLong("rib.primary.minion_lifespan_ticks");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        abilityManager.registerPrimaryAbility(TrimPattern.RIB, this::activateRibPrimary);
    }



    public void activateRibPrimary(Player player) {
        if (!configManager.isTrimEnabled("rib")) {
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RIB) || cooldownManager.isOnCooldown(player, TrimPattern.RIB)) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        UUID ownerUUID = player.getUniqueId();

        world.playSound(playerLoc, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 1.0f);
        world.playSound(playerLoc, Sound.BLOCK_BONE_BLOCK_PLACE, 1.0f, 1.2f);
        createBoneEffect(player);

        playerMinionMap.putIfAbsent(ownerUUID, new ArrayList<>());
        int spawnCount = 3;

        for (int i = 0; i < spawnCount; i++) {
            double angle = Math.toRadians((360.0 / spawnCount) * i);
            double offsetX = Math.cos(angle) * 3;
            double offsetZ = Math.sin(angle) * 3;
            Location spawnLoc = playerLoc.clone().add(offsetX, 0, offsetZ);

            // Spawn the Bogged entity
            Bogged bogged = world.spawn(spawnLoc, Bogged.class);

            // Set properties
            bogged.setCustomName(ChatColor.WHITE + "Bone Warrior");
            bogged.setCustomNameVisible(true);
            bogged.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, ownerUUID.toString());

            // Apply equipment and buffs
            ItemStack bow = new ItemStack(Material.BOW);
            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
            bogged.getEquipment().setHelmet(helmet);
            bogged.getEquipment().setHelmetDropChance(0.0f);
            bow.addUnsafeEnchantment(Enchantment.POWER, 5);
            Objects.requireNonNull(bogged.getEquipment()).setItemInMainHand(bow);
            bogged.getEquipment().setItemInMainHandDropChance(0.0f);
            applyBuffs(bogged);

            // Add to the owner's minion list
            playerMinionMap.get(ownerUUID).add(bogged);

            // Set initial target if one exists
            LivingEntity currentTarget = playerTargetMap.get(ownerUUID);
            if (isValidTarget(currentTarget, player)) {
                bogged.setTarget(currentTarget);
            }

            // **IMPROVEMENT**: Add a despawn timer for cleanup
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (bogged.isValid()) {
                        bogged.getWorld().spawnParticle(Particle.SMOKE, bogged.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0);
                        bogged.remove();
                    }
                }
            }.runTaskLater(plugin, MINION_LIFESPAN_TICKS);
        }

        cooldownManager.setCooldown(player, TrimPattern.RIB, RIB_COOLDOWN);
        Messaging.sendTrimMessage(player, "Rib", ChatColor.WHITE, "You have summoned Bone Warriors!");
    }

    /**
     * Event handler for when a player with minions attacks something.
     * This makes the minions focus fire on the player's target.
     */
    @EventHandler
    public void onOwnerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player owner) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // Only act if the player actually has minions
        List<Mob> minions = playerMinionMap.get(owner.getUniqueId());
        if (minions == null || minions.isEmpty()) {
            return;
        }

        if (!isValidTarget(target, owner)) {
            playerTargetMap.remove(owner.getUniqueId());
            return; // Don't target own minions, trusted players, etc.
        }

        // Update the primary target and command minions to attack
        playerTargetMap.put(owner.getUniqueId(), target);
        for (Mob minion : minions) {
            if (minion.isValid()) {
                minion.setTarget(target);
            }
        }
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        // Check if the player is sneaking when they press the offhand key
        if (!configManager.isTrimEnabled("rib")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            // This is important: it prevents the player's hands from actually swapping items
            event.setCancelled(true);

            // Activate the ability
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }


    /**
     * BEHAVIOR IMPROVEMENT: Defensive AI
     * Makes minions attack any entity that damages their owner.
     */
    @EventHandler
    public void onOwnerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player owner) || !(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }

        List<Mob> minions = playerMinionMap.get(owner.getUniqueId());
        if (minions == null || minions.isEmpty()) {
            return;
        }

        // Command all minions to defend their owner
        if (isValidTarget(attacker, owner)) {
            playerTargetMap.put(owner.getUniqueId(), attacker); // New target is the attacker
            for (Mob minion : minions) {
                if (minion.isValid()) {
                    minion.setTarget(attacker);
                }
            }
        }
    }

    /**
     * Prevents minions from targeting their owner, trusted players, or allied minions.
     */
    @EventHandler
    public void onMinionTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Mob minion)) return;
        String ownerUUIDString = minion.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (ownerUUIDString == null) return;

        Player owner = Bukkit.getPlayer(UUID.fromString(ownerUUIDString));
        if (owner == null) return;

        if (!isValidTarget(event.getTarget(), owner)) {
            event.setCancelled(true);
        }
    }

    /**
     * MEMORY LEAK FIX: Clean up when minions die.
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity deadEntity = event.getEntity();
        String ownerUUIDString = deadEntity.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (ownerUUIDString == null) return;

        UUID ownerUUID = UUID.fromString(ownerUUIDString);
        List<Mob> minions = playerMinionMap.get(ownerUUID);
        if (minions != null) {
            minions.remove(deadEntity);
        }
    }

    /**
     * MEMORY LEAK FIX: Clean up when the owner logs out.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID ownerUUID = event.getPlayer().getUniqueId();
        List<Mob> minions = playerMinionMap.remove(ownerUUID);
        if (minions != null) {
            for (Mob minion : minions) {
                if (minion.isValid()) {
                    minion.remove();
                }
            }
        }
        playerTargetMap.remove(ownerUUID);
    }

    // --- HELPER METHODS ---

    /**
     * A central method to check if a target is valid for a player's minions.
     * @param target The potential target entity.
     * @param owner The owner of the minions.
     * @return True if the target is valid, false otherwise.
     */
    private boolean isValidTarget(Entity target, Player owner) {
        if (target == null || !target.isValid() || target.equals(owner)) {
            return false;
        }
        // Don't target trusted players
        if (target instanceof Player targetPlayer && trustManager.isTrusted(owner.getUniqueId(), targetPlayer.getUniqueId())) {
            return false;
        }
        // Don't target other minions of the same owner
        String targetOwnerUUID = target.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        return !(targetOwnerUUID != null && targetOwnerUUID.equals(owner.getUniqueId().toString()));
    }

    private void applyBuffs(Mob mob) {
        AttributeInstance healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(40.0);
            mob.setHealth(40.0);
        }

        AttributeInstance dmgAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(10.0); // Note: This primarily affects melee, bow damage is from enchantments.
        }
    }

    private void createBoneEffect(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
            double x = Math.cos(angle) * 5;
            double z = Math.sin(angle) * 5;
            Location particleLoc = loc.clone().add(x, 0.5, z);
            world.spawnParticle(Particle.BLOCK, particleLoc, 5, 0.2, 0.2, 0.2, 0, Bukkit.createBlockData(Material.BONE_BLOCK));
        }
    }
}
