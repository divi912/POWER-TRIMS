package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.*;
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
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class SnoutTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    private final long ROAR_COOLDOWN;
    private final long MINION_LIFESPAN_TICKS;
    private static final NamespacedKey SUMMONER_KEY;
    static {
        SUMMONER_KEY = new NamespacedKey("powertrims", "snout_summoner_uuid");
    }

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
            event.setCancelled(true);

            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateSnoutPrimary(Player player) {
        if (!configManager.isTrimEnabled("snout")) return;
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SNOUT) || cooldownManager.isOnCooldown(player, TrimPattern.SNOUT)) return;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        cooldownManager.setCooldown(player, TrimPattern.SNOUT, ROAR_COOLDOWN);
        Messaging.sendTrimMessage(player, "Snout", ChatColor.DARK_RED, "You have summoned your Minions!");
        playerMinions.putIfAbsent(player.getUniqueId(), new ArrayList<>());

        new BukkitRunnable() {
            private int spawnedCount = 0;

            @Override
            public void run() {
                int minionsToSpawn = 5;
                if (spawnedCount >= minionsToSpawn) {
                    this.cancel();
                    return;
                }

                double angle = Math.toRadians((360.0 / minionsToSpawn) * spawnedCount);
                Location spawnLoc = player.getLocation().clone().add(Math.cos(angle) * 3.5, 0, Math.sin(angle) * 3.5);

                playBlackstonePillarAnimation(spawnLoc, (finalLocation) -> {
                    WitherSkeleton skeleton = spawnMinion(player, finalLocation);
                    playerMinions.get(player.getUniqueId()).add(skeleton);
                    scheduleMinionRemoval(skeleton, MINION_LIFESPAN_TICKS);
                });

                spawnedCount++;
            }
        }.runTaskTimer(plugin, 0L, 25L);
    }

    private void playBlackstonePillarAnimation(Location location, Consumer<Location> onSummonCallback) {
        int pillarHeight = 3;
        int eruptionTicks = 8;
        int staggerDelay = 2;
        int lingerTicks = 10;
        List<BlockDisplay> pillarBlocks = new ArrayList<>();
        long totalEruptionTime = ((long)(pillarHeight - 1) * staggerDelay) + eruptionTicks;

        location.getWorld().playSound(location, Sound.BLOCK_BASALT_BREAK, 1.5f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > totalEruptionTime + lingerTicks) {
                    this.cancel();
                    return;
                }
                location.getWorld().spawnParticle(Particle.FLAME, location, 2, 0.5, 0.1, 0.5, 0.01);
                location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 1, 0.5, 0.1, 0.5, 0);
            }
        }.runTaskTimer(plugin, 0L, 2L);

        for (int i = 0; i < pillarHeight; i++) {
            final int finalYOffset = i;
            Location startPos = location.clone().add(0, i - pillarHeight, 0);

            BlockDisplay block = location.getWorld().spawn(startPos, BlockDisplay.class, bd -> {
                bd.setBlock(Material.BLACKSTONE.createBlockData());
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
                location.getWorld().playSound(location, Sound.ENTITY_WITHER_SKELETON_HURT, 1.2f, 0.8f);
                location.getWorld().spawnParticle(Particle.LAVA, location, 10, 0.3, 0.1, 0.3, 0);
                for (BlockDisplay block : pillarBlocks) {
                    if (block.isValid()) {
                        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0, 0.5, 0), 40, 0.5, 0.5, 0.5, Material.BLACKSTONE.createBlockData());
                        block.remove();
                    }
                }
                onSummonCallback.accept(location);
            }
        }.runTaskLater(plugin, totalEruptionTime + lingerTicks);
    }

    private void playMinionDespawnAnimation(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_NETHERRACK_BREAK, 1.0f, 0.8f);
        location.getWorld().spawnParticle(Particle.SOUL, location, 15, 0.3, 0.5, 0.3, 0.05);

        int particleCount = 20;
        int animationTicks = 25;

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Location startPos = location.clone().add(r.nextGaussian() * 0.5, r.nextGaussian() * 0.8, r.nextGaussian() * 0.5);
            BlockDisplay particle = location.getWorld().spawn(startPos, BlockDisplay.class, bd -> {
                bd.setBlock(Material.BLACKSTONE.createBlockData());
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

    private WitherSkeleton spawnMinion(Player owner, Location location) {
        return owner.getWorld().spawn(location, WitherSkeleton.class, skel -> {
            skel.getPersistentDataContainer().set(SUMMONER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
            skel.setCustomName(ChatColor.DARK_GRAY + "Necromancer's Minion");
            skel.setCustomNameVisible(true);
            skel.setCollidable(false);
            skel.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1));
            skel.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
            skel.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));
            Optional.of(skel.getEquipment()).ifPresent(eq -> {
                eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                eq.setItemInMainHandDropChance(0.0f);
            });
        });
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player owner = null;
        LivingEntity target = null;

        if (event.getDamager() instanceof Player) {
            owner = (Player) event.getDamager();
            target = (LivingEntity) event.getEntity();
        }
        else if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            owner = (Player) event.getEntity();
            target = (LivingEntity) event.getDamager();
        }

        if (owner != null) {
            List<WitherSkeleton> minions = playerMinions.get(owner.getUniqueId());
            if (minions != null && !minions.isEmpty() && isValidTarget(target, owner)) {
                for (WitherSkeleton minion : minions) {
                    if (minion.isValid()) {
                        minion.setTarget(target);
                    }
                }
            }
        }
    }

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

    private void scheduleMinionRemoval(WitherSkeleton minion, long ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (minion.isValid()) {
                    playMinionDespawnAnimation(minion.getLocation());
                    minion.remove();
                }
            }
        }.runTaskLater(plugin, ticks);
    }

    private boolean isValidTarget(Entity target, Player owner) {
        if (target == null || !target.isValid() || target.equals(owner)) {
            return false;
        }

        if (target instanceof Player && trustManager.isTrusted(owner.getUniqueId(), target.getUniqueId())) {
            return false;
        }

        String targetOwnerUUID = target.getPersistentDataContainer().get(SUMMONER_KEY, PersistentDataType.STRING);
        return !(targetOwnerUUID != null && targetOwnerUUID.equals(owner.getUniqueId().toString()));
    }


}