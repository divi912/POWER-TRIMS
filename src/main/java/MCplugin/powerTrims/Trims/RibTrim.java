package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class RibTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final NamespacedKey effectKey;
    private static final long RIB_COOLDOWN = 60000; // 1 minute cooldown

    private static final Map<UUID, LivingEntity> playerTargetMap = new HashMap<>();
    private static final Map<UUID, List<Mob>> playerBoggedMap = new HashMap<>();

    public RibTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.effectKey = new NamespacedKey(plugin, "rib_trim_effect");
        RibPassive();
    }

    private void RibPassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RIB)) {
                    if (!player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, true, false, true));
                        player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                    }
                } else {
                    if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                        player.removePotionEffect(PotionEffectType.RESISTANCE);
                        player.getPersistentDataContainer().remove(effectKey);
                    }
                }
            }
        }, 0L, 20L);
    }

    public void RibPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RIB) || cooldownManager.isOnCooldown(player, TrimPattern.RIB)) {
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        UUID ownerUUID = player.getUniqueId();
        NamespacedKey ownerKey = new NamespacedKey(plugin, "owner");

        world.playSound(playerLoc, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 1.0f);
        world.playSound(playerLoc, Sound.BLOCK_BONE_BLOCK_PLACE, 1.0f, 1.2f);
        createBoneEffect(player);

        playerBoggedMap.putIfAbsent(ownerUUID, new ArrayList<>());
        int spawnCount = 3;

        for (int i = 0; i < spawnCount; i++) {
            double angle = Math.toRadians((360.0 / spawnCount) * i);
            double offsetX = Math.cos(angle) * 3;
            double offsetZ = Math.sin(angle) * 3;
            Location spawnLoc = playerLoc.clone().add(offsetX, 0, offsetZ);

            LivingEntity boggedEntity = (LivingEntity) world.spawnEntity(spawnLoc, EntityType.BOGGED);
            boggedEntity.setCustomName(ChatColor.WHITE + "Bone Warrior");
            boggedEntity.setCustomNameVisible(true);
            boggedEntity.setRemoveWhenFarAway(true);
            boggedEntity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerUUID.toString());

            ItemStack bow = new ItemStack(Material.BOW);
            bow.addUnsafeEnchantment(Enchantment.POWER, 5);
            boggedEntity.getEquipment().setItemInMainHand(bow);
            boggedEntity.getEquipment().setItemInMainHandDropChance(0.0f);

            if (boggedEntity instanceof Mob mob) {
                applyBuffs(mob);
                playerBoggedMap.get(ownerUUID).add(mob);

                // Immediately try to set the target if the player has recently attacked something
                LivingEntity currentTarget = playerTargetMap.get(ownerUUID);
                if (currentTarget != null && currentTarget.isValid() && !currentTarget.equals(player)) {
                    mob.setTarget(currentTarget);
                    // Optionally, make them look at the target immediately
                    Location current = boggedEntity.getLocation();
                    Vector direction = currentTarget.getLocation().toVector().subtract(current.toVector());
                    if (!direction.equals(new Vector(0, 0, 0))) {
                        current.setDirection(direction);
                        boggedEntity.teleport(current);
                    }
                }

                // Keep checking for target updates
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!boggedEntity.isValid()) {
                            cancel();
                            return;
                        }
                        LivingEntity target = playerTargetMap.get(ownerUUID);
                        if (target != null && target.isValid() && !target.equals(player)) {
                            if (target.getType() == EntityType.BOGGED &&
                                    ownerUUID.toString().equals(target.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) {
                                return;
                            }
                            mob.setTarget(target);
                            Location current = boggedEntity.getLocation();
                            Vector direction = target.getLocation().toVector().subtract(current.toVector());
                            if (!direction.equals(new Vector(0, 0, 0))) {
                                current.setDirection(direction);
                                boggedEntity.teleport(current);
                            }
                        } else {
                            mob.setTarget(null);
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }

        cooldownManager.setCooldown(player, TrimPattern.RIB, RIB_COOLDOWN);
        player.sendMessage("§8[§fRib§8] §7You have summoned Bone Warriors!");
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            RibPrimary(player);
        }
    }


    private void applyBuffs(Mob mob) {
        AttributeInstance healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null && healthAttr.getBaseValue() < 40.0) {
            healthAttr.setBaseValue(40.0);
            mob.setHealth(40.0);
        }

        AttributeInstance dmgAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null && dmgAttr.getBaseValue() < 10.0) {
            dmgAttr.setBaseValue(10.0);
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

        for (double y = 0; y < 2; y += 0.2) {
            double angle = y * Math.PI * 4;
            double radius = 5 * (1 - y / 2);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = loc.clone().add(x, y + 0.5, z);
            world.spawnParticle(Particle.FALLING_DUST, particleLoc, 3, 0.1, 0.1, 0.1, 0, Bukkit.createBlockData(Material.BONE_BLOCK));
        }

        world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 1);
        world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.01);
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target) {
            UUID ownerUUID = player.getUniqueId();

            if (target.getType() == EntityType.BOGGED) {
                String targetOwner = target.getPersistentDataContainer().get(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING);
                if (targetOwner != null && targetOwner.equals(ownerUUID.toString())) {
                    return;
                }
            }

            playerTargetMap.put(ownerUUID, target);

            if (playerBoggedMap.containsKey(ownerUUID)) {
                for (Mob bogged : playerBoggedMap.get(ownerUUID)) {
                    if (bogged.isValid() && target.isValid() && !target.equals(player)) {
                        bogged.setTarget(target);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getEntity() instanceof Mob bogged && bogged.getType() == EntityType.BOGGED) {
            String ownerUUID = bogged.getPersistentDataContainer().get(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING);
            if (ownerUUID == null) return;

            if (event.getTarget() instanceof Player target && target.getUniqueId().toString().equals(ownerUUID)) {
                event.setCancelled(true);
            }

            if (event.getTarget() instanceof Mob targetMob && targetMob.getType() == EntityType.BOGGED) {
                String targetOwner = targetMob.getPersistentDataContainer().get(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING);
                if (targetOwner != null && targetOwner.equals(ownerUUID)) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
