package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SpireTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final NamespacedKey effectKey;
    private final NamespacedKey vulnerableKey;
    private final Set<UUID> markedTargets;
    private final Set<UUID> dashingPlayers;

    private static final int DASH_DISTANCE = 8;
    private static final double DASH_SPEED = 2.0; // Blocks per tick
    private static final double KNOCKBACK_STRENGTH = 2.0;
    private static final int SLOW_DURATION = 60; // 3 seconds
    private static final int VULNERABLE_DURATION = 100; // 5 seconds
    private static final double DAMAGE_AMPLIFICATION = 0.4; // increased damage
    private static final long ABILITY_COOLDOWN = 30000; // 30 seconds cooldown

    public SpireTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.effectKey = new NamespacedKey(plugin, "spire_trim_effect");
        this.vulnerableKey = new NamespacedKey(plugin, "spire_vulnerable_effect");
        this.markedTargets = new HashSet<>();
        this.dashingPlayers = new HashSet<>();
        SpirePassive();
    }

    private void SpirePassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.SPIRE)) {
                    if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false, true));
                        player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                    }
                } else {
                    if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                        player.removePotionEffect(PotionEffectType.SPEED);
                        player.getPersistentDataContainer().remove(effectKey);
                    }
                }
            }
        }, 0L, 20L);
    }

    public void SpirePrimary(Player player) {
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
                        hitEntities.add(entity);
                        handleEntityCollision((LivingEntity) entity, direction);
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
                player.sendMessage(ChatColor.GREEN + "You used " + ChatColor.GOLD + "Spire Dash" + ChatColor.GREEN + "!");
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

    private void handleEntityCollision(LivingEntity target, Vector dashDirection) {
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
            ((Player) target).sendMessage(ChatColor.RED + "You've been marked by a Spire Dash!");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // If the target has been marked, apply damage amplification
        if (markedTargets.contains(target.getUniqueId())) {
            event.setDamage(event.getDamage() * (1 + DAMAGE_AMPLIFICATION)); // Amplify damage by 15%
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
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            SpirePrimary(player);
        }
    }
}
