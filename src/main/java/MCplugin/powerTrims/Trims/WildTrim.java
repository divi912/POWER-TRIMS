package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.*;


public class WildTrim implements Listener {

    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager; 
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final Random random = new Random();

    private final int PASSIVE_TRIGGER_HEALTH;
    private final int PASSIVE_COOLDOWN_SECONDS;
    private final long PRIMARY_COOLDOWN;
    private final double GRAPPLE_RANGE;
    private final int POISON_DURATION_TICKS;
    private final double GRAPPLE_SPEED;
    private final double ROOT_TRAP_RADIUS_XZ;
    private final double ROOT_TRAP_RADIUS_Y;
    private final int ROOT_TRAP_DURATION_TICKS;

    private final Map<UUID, Long> passiveCooldowns = new HashMap<>();
    private final Set<UUID> frozenEntities = new HashSet<>();


    public WildTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager; 
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        PASSIVE_TRIGGER_HEALTH = configManager.getInt("wild.passive.trigger_health");
        PASSIVE_COOLDOWN_SECONDS = configManager.getInt("wild.passive.cooldown_seconds");
        PRIMARY_COOLDOWN = configManager.getLong("wild.primary.cooldown");
        GRAPPLE_RANGE = configManager.getDouble("wild.primary.grapple_range");
        POISON_DURATION_TICKS = configManager.getInt("wild.primary.poison_duration_ticks");
        GRAPPLE_SPEED = configManager.getDouble("wild.primary.grapple_speed");
        ROOT_TRAP_RADIUS_XZ = configManager.getDouble("wild.passive.root_trap_radius_xz");
        ROOT_TRAP_RADIUS_Y = configManager.getDouble("wild.passive.root_trap_radius_y");
        ROOT_TRAP_DURATION_TICKS = configManager.getInt("wild.passive.root_trap_duration_ticks");

        abilityManager.registerPrimaryAbility(TrimPattern.WILD, this::WildPrimary);
    }



    public void WildPrimary(Player player) {
        if (!configManager.isTrimEnabled("wild")) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WILD)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.WILD)) return;

        Location start = player.getEyeLocation();
        Vector direction = player.getLocation().getDirection().normalize();
        double range = GRAPPLE_RANGE;
        Player wildUser = player; 

        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.0f);

        boolean abilityUsed = false;

        RayTraceResult entityHit = player.getWorld().rayTraceEntities(
                start,
                direction,
                range,
                entity -> entity instanceof LivingEntity && entity != wildUser
        );

        if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity targetEntity) {
            if (targetEntity instanceof Player targetPlayer && trustManager.isTrusted(wildUser.getUniqueId(), targetPlayer.getUniqueId())) {
                Messaging.sendTrimMessage(player, "Wild", ChatColor.GREEN, "Grappling to trusted player!");
                visualizeGrapple(player, targetEntity.getLocation().add(0, 1, 0));
                smoothlyPullPlayer(player, targetEntity.getLocation().add(0, 1, 0));
            } else {
                Messaging.sendTrimMessage(player, "Wild", ChatColor.GREEN, "Grappling to entity!");
                visualizeGrapple(player, targetEntity.getLocation().add(0, 1, 0));
                smoothlyPullPlayer(player, targetEntity.getLocation().add(0, 1, 0));
                targetEntity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, POISON_DURATION_TICKS, 1, true, false, true));
                if(targetEntity instanceof Player) Messaging.sendTrimMessage((Player) targetEntity, "Wild", ChatColor.RED, "You have been Poisoned for " + (POISON_DURATION_TICKS/20) + " sec!");
                abilityUsed = true;
            }
        } else {
            Block targetBlock = player.getTargetBlockExact((int) range);
            if (targetBlock != null && !targetBlock.getType().isAir()) {
                Messaging.sendTrimMessage(player, "Wild", ChatColor.GREEN, "Grappling to block!");
                visualizeGrapple(player, targetBlock.getLocation().add(0.5, 1, 0.5));
                smoothlyPullPlayer(player, targetBlock.getLocation().add(0.5, 1, 0.5));
                abilityUsed = true;
            } else {
                Messaging.sendTrimMessage(player, "Wild", ChatColor.RED, "No valid target found!");
            }
        }

        if (abilityUsed) {
            cooldownManager.setCooldown(player, TrimPattern.WILD, PRIMARY_COOLDOWN);
        }
    }


    private void visualizeGrapple(Player player, Location target) {
        Location start = player.getEyeLocation();
        World world = player.getWorld();
        if (world == null) return;

        final List<BlockDisplay> vineParts = new ArrayList<>();
        final Material vineMaterial = Material.VINE;
        Vector travel = target.toVector().subtract(start.toVector());
        int segments = (int) (travel.length() * 2.0);
        if (segments == 0) return;
        Vector step = travel.clone().multiply(1.0 / segments);

        new BukkitRunnable() {
            int currentSegment = 0;
            @Override
            public void run() {
                if (currentSegment >= segments) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            vineParts.forEach(Entity::remove);
                        }
                    }.runTaskLater(plugin, 20L);
                    this.cancel();
                    return;
                }

                Location currentPos = start.clone().add(step.clone().multiply(currentSegment));
                BlockDisplay part = world.spawn(currentPos, BlockDisplay.class, bd -> {
                    bd.setBlock(vineMaterial.createBlockData());
                    Transformation t = bd.getTransformation();
                    t.getScale().set(0.2f);
                    bd.setTransformation(t);
                });
                vineParts.add(part);
                currentSegment++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }


    private void smoothlyPullPlayer(Player player, Location target) {
        player.setGravity(false);
        player.setFallDistance(0);

        new BukkitRunnable() {
            int ticks = 0;
            final double maxSpeed = GRAPPLE_SPEED;
            boolean reachedTarget = false;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    finishGrapple();
                    return;
                }

                double distanceSquared = player.getLocation().distanceSquared(target);

                if (distanceSquared < 1.5) { 
                    reachedTarget = true;
                    finishGrapple();
                    return;
                }

                if (ticks++ > 30) { 
                    finishGrapple();
                    return;
                }

                Vector pullVector = target.toVector().subtract(player.getLocation().toVector());
                double length = pullVector.length();
                double speed = Math.min(maxSpeed, length * 0.28); 

                player.setVelocity(pullVector.normalize().multiply(speed));

                playWindEffect(player);
                if (ticks % 5 == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.2f);
                }
            }

            private void finishGrapple() {
                player.setGravity(true);
                if (reachedTarget) {
                    player.setVelocity(new Vector(0, 0.3, 0));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                }
                this.cancel();
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void playWindEffect(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        if (world == null) return;

        for (int i = 0; i < 3; i++) {
            Location spawnLoc = loc.clone().add((random.nextDouble() - 0.5) * 1.5, (random.nextDouble() - 0.5) * 1.5 + 1, (random.nextDouble() - 0.5) * 1.5);
            BlockDisplay block = world.spawn(spawnLoc, BlockDisplay.class, bd -> {
                bd.setBlock(Material.LIGHT_GRAY_WOOL.createBlockData());
                Transformation t = bd.getTransformation();
                t.getScale().set((float) (0.1 + random.nextDouble() * 0.1));
                bd.setTransformation(t);
            });

            Vector velocity = player.getVelocity().clone().multiply(-0.5).add(new Vector(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).multiply(0.2));
            new BukkitRunnable() {
                int life = 0;
                @Override
                public void run() {
                    if (life++ > 10 || !block.isValid()) {
                        block.remove();
                        this.cancel();
                        return;
                    }
                    block.teleport(block.getLocation().add(velocity));
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }


    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (!configManager.isTrimEnabled("wild")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);

            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }




    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WILD)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getHealth() > PASSIVE_TRIGGER_HEALTH) return;
            if (isPassiveOnCooldown(player)) {
                Messaging.sendTrimMessage(player, "Wild", ChatColor.RED, "Root Trap is on cooldown!");
                return;
            }

            activateRootTrap(player);
            setPassiveCooldown(player);
        }, 1L); 
    }

    public void activateRootTrap(Player player) {
        if (isPassiveOnCooldown(player)) {
            Messaging.sendTrimMessage(player, "Wild", ChatColor.RED, "Root Trap is on cooldown!");
            return;
        }

        setPassiveCooldown(player);
        Messaging.sendTrimMessage(player, "Wild", ChatColor.GREEN, "You activated Root Trap!");

        List<LivingEntity> affectedEntities = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(ROOT_TRAP_RADIUS_XZ, ROOT_TRAP_RADIUS_Y, ROOT_TRAP_RADIUS_XZ)) {
            if (entity instanceof LivingEntity && entity != player) {
                LivingEntity target = (LivingEntity) entity;
                if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue; 
                }
                affectedEntities.add(target);
                frozenEntities.add(target.getUniqueId());
                spawnVinesAnimation(target.getLocation());
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (LivingEntity entity : affectedEntities) {
                    if (frozenEntities.contains(entity.getUniqueId())) {
                        entity.setVelocity(new Vector(0, 0, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (LivingEntity entity : affectedEntities) {
                    frozenEntities.remove(entity.getUniqueId());
                }
            }
        }.runTaskLater(plugin, ROOT_TRAP_DURATION_TICKS);
    }


    private void spawnVinesAnimation(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        final List<BlockDisplay> vines = new ArrayList<>();
        final Material vineMaterial = Material.OAK_LOG;
        final int duration = ROOT_TRAP_DURATION_TICKS;

        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            Location spawnLoc = location.clone().add(Math.cos(angle) * 1.2, 0, Math.sin(angle) * 1.2);
            BlockDisplay vine = world.spawn(spawnLoc, BlockDisplay.class, bd -> {
                bd.setBlock(vineMaterial.createBlockData());
                Transformation t = bd.getTransformation();
                t.getScale().set(0.1f, 0.01f, 0.1f);
                bd.setTransformation(t);
            });
            vines.add(vine);
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > duration) {
                    new BukkitRunnable() {
                        int shrinkTicks = 0;
                        @Override
                        public void run() {
                            if (shrinkTicks++ > 20) {
                                vines.forEach(Entity::remove);
                                this.cancel();
                                return;
                            }
                            for (BlockDisplay vine : vines) {
                                if (!vine.isValid()) continue;
                                Transformation t = vine.getTransformation();
                                t.getScale().mul(0.8f);
                                vine.setTransformation(t);
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                    this.cancel();
                    return;
                }

                for (BlockDisplay vine : vines) {
                    if (!vine.isValid()) continue;
                    Transformation t = vine.getTransformation();
                    if (t.getScale().y < 2.0f) {
                        t.getScale().add(0, 0.1f, 0);
                        vine.setTransformation(t);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenEntities.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }



    private boolean isPassiveOnCooldown(Player player) {
        return passiveCooldowns.containsKey(player.getUniqueId()) && (System.currentTimeMillis() < passiveCooldowns.get(player.getUniqueId()));
    }

    private void setPassiveCooldown(Player player) {
        passiveCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (PASSIVE_COOLDOWN_SECONDS * 1000L));
    }


}