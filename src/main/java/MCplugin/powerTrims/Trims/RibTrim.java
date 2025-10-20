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
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class RibTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    private final long RIB_COOLDOWN;
    private final long MINION_LIFESPAN_TICKS;
    private static final NamespacedKey OWNER_KEY;

    static {
        OWNER_KEY = new NamespacedKey("powertrims", "owner_uuid");
    }

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
        if (!configManager.isTrimEnabled("rib")) return;
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.RIB) || cooldownManager.isOnCooldown(player, TrimPattern.RIB)) return;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) return;

        playerMinionMap.putIfAbsent(player.getUniqueId(), new ArrayList<>());
        int spawnCount = 3;

        for (int i = 0; i < spawnCount; i++) {
            double angle = Math.toRadians((360.0 / spawnCount) * i);
            Location spawnLoc = player.getLocation().clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);

            playSummonAnimation(spawnLoc, (finalLocation) -> {
                Bogged bogged = spawnMinion(player, finalLocation);
                playerMinionMap.get(player.getUniqueId()).add(bogged);
                LivingEntity currentTarget = playerTargetMap.get(player.getUniqueId());
                if (isValidTarget(currentTarget, player)) {
                    bogged.setTarget(currentTarget);
                }
                scheduleMinionRemoval(bogged, MINION_LIFESPAN_TICKS);
            });
        }

        cooldownManager.setCooldown(player, TrimPattern.RIB, RIB_COOLDOWN);
    }

    private void playSummonAnimation(Location location, Consumer<Location> onSummonCallback) {
        int pillarHeight = 3;
        int eruptionTicks = 10;
        int staggerDelay = 3;
        int lingerTicks = 15;
        List<BlockDisplay> pillarBlocks = new ArrayList<>();
        long totalEruptionTime = ((long)(pillarHeight - 1) * staggerDelay) + eruptionTicks;

        location.getWorld().playSound(location, Sound.BLOCK_BONE_BLOCK_PLACE, 1.5f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > totalEruptionTime + lingerTicks) {
                    this.cancel();
                    return;
                }
                location.getWorld().spawnParticle(Particle.SOUL, location, 3, 0.5, 0.1, 0.5, 0.01);
                location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 1, 0.5, 0.1, 0.5, 0);
            }
        }.runTaskTimer(plugin, 0L, 2L);


        for (int i = 0; i < pillarHeight; i++) {
            int finalYOffset = i;
            Location startPos = location.clone().add(0, i - pillarHeight, 0);

            BlockDisplay block = location.getWorld().spawn(startPos, BlockDisplay.class, bd -> {
                bd.setBlock(Material.BONE_BLOCK.createBlockData());
                bd.setInterpolationDuration(eruptionTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(1.0f);
                bd.setTransformation(t);
            });
            pillarBlocks.add(block);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.isValid()) {
                        block.teleport(location.clone().add(0, finalYOffset, 0));
                    }
                }
            }.runTaskLater(plugin, i * staggerDelay);
        }


        new BukkitRunnable() {
            @Override
            public void run() {
                location.getWorld().playSound(location, Sound.ENTITY_WITHER_SKELETON_HURT, 1.2f, 0.5f);
                location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location.clone().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.05);
                for (BlockDisplay block : pillarBlocks) {
                    if (block.isValid()) {
                        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0, 0.5, 0), 40, 0.5, 0.5, 0.5, Material.BONE_BLOCK.createBlockData());
                        block.remove();
                    }
                }
                onSummonCallback.accept(location);
            }
        }.runTaskLater(plugin, totalEruptionTime + lingerTicks);
    }


    private void playMinionDespawnAnimation(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_BONE_BLOCK_BREAK, 1.0f, 0.8f);
        location.getWorld().spawnParticle(Particle.SOUL, location, 15, 0.3, 0.5, 0.3, 0.05);

        int particleCount = 20;
        int animationTicks = 25;

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Location startPos = location.clone().add(r.nextGaussian() * 0.5, r.nextGaussian() * 0.8, r.nextGaussian() * 0.5);
            BlockDisplay particle = location.getWorld().spawn(startPos, BlockDisplay.class, bd -> {
                bd.setBlock(Material.BONE_BLOCK.createBlockData());
                bd.setInterpolationDuration(animationTicks);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(r.nextFloat() * 0.4f + 0.2f);
                bd.setTransformation(t);
            });

            particle.teleport(location.clone().subtract(0, 1, 0));
            Transformation finalTransform = particle.getTransformation();
            finalTransform.getScale().set(0f);
            particle.setTransformation(finalTransform);
            new BukkitRunnable() { @Override public void run() { if (particle.isValid()) particle.remove(); }}.runTaskLater(plugin, animationTicks + 1);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                location.getWorld().spawnParticle(Particle.SMOKE, location, 10, 0.5, 0.2, 0.5, 0);
            }
        }.runTaskLater(plugin, animationTicks);
    }

    private Bogged spawnMinion(Player owner, Location location) {
        return owner.getWorld().spawn(location, Bogged.class, bogged -> {
            bogged.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
            bogged.setCustomName(ChatColor.WHITE + "Bone Warrior");
            bogged.setCustomNameVisible(true);

            ItemStack bow = new ItemStack(Material.BOW);
            bow.addUnsafeEnchantment(Enchantment.POWER, 5);
            Optional.of(bogged.getEquipment()).ifPresent(eq -> {
                eq.setItemInMainHand(bow);
                eq.setItemInMainHandDropChance(0.0f);
                eq.setHelmet(new ItemStack(Material.LEATHER_HELMET));
                eq.setHelmetDropChance(0.0f);
            });
            applyBuffs(bogged);
        });
    }



    private void scheduleMinionRemoval(Mob minion, long ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (minion.isValid()) {
                    playMinionDespawnAnimation(minion.getEyeLocation());
                    minion.remove();
                }
            }
        }.runTaskLater(plugin, ticks);
    }


    @EventHandler
    public void onOwnerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player owner) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        List<Mob> minions = playerMinionMap.get(owner.getUniqueId());
        if (minions == null || minions.isEmpty()) {
            return;
        }

        if (!isValidTarget(target, owner)) {
            playerTargetMap.remove(owner.getUniqueId());
            return;
        }

        playerTargetMap.put(owner.getUniqueId(), target);
        for (Mob minion : minions) {
            if (minion.isValid()) {
                minion.setTarget(target);
            }
        }
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (!configManager.isTrimEnabled("rib")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);

            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }


    @EventHandler
    public void onOwnerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player owner) || !(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }

        List<Mob> minions = playerMinionMap.get(owner.getUniqueId());
        if (minions == null || minions.isEmpty()) {
            return;
        }

        if (isValidTarget(attacker, owner)) {
            playerTargetMap.put(owner.getUniqueId(), attacker);
            for (Mob minion : minions) {
                if (minion.isValid()) {
                    minion.setTarget(attacker);
                }
            }
        }
    }

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

    private boolean isValidTarget(Entity target, Player owner) {
        if (target == null || !target.isValid() || target.equals(owner)) {
            return false;
        }
        if (target instanceof Player targetPlayer && trustManager.isTrusted(owner.getUniqueId(), targetPlayer.getUniqueId())) {
            return false;
        }
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
            dmgAttr.setBaseValue(10.0);
        }
    }


}