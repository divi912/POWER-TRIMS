package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.Logic.PersistentTrustManager;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BoltTrim implements Listener {

    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;

    public BoltTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            activateBoltPrimary(event.getPlayer());
        }
    }

    public void activateBoltPrimary(Player player) {
        if (!configManager.isTrimEnabled("bolt")) {
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.BOLT)) {
            return;
        }
        if (cooldownManager.isOnCooldown(player, TrimPattern.BOLT)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use this ability in the current region.");
            return;
        }

        long cooldown = configManager.getLong("bolt.primary.cooldown");
        int chainRange = configManager.getInt("bolt.primary.chain_range");
        int maxChains = configManager.getInt("bolt.primary.max_chains");
        double initialDamage = configManager.getDouble("bolt.primary.initial_damage");
        double subsequentDamage = configManager.getDouble("bolt.primary.subsequent_damage");
        int targetRange = configManager.getInt("bolt.primary.target_range");
        int weaknessDuration = configManager.getInt("bolt.primary.weakness_duration"); 
        int weaknessAmplifier = configManager.getInt("bolt.primary.weakness_amplifier"); 

        LivingEntity target = getTarget(player, targetRange);

        if (target == null) {
            player.sendMessage(ChatColor.GRAY + "No target in sight.");
            return;
        }

        playLightningAnimation(target.getLocation());
        target.damage(initialDamage, player);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessDuration, weaknessAmplifier));

        List<LivingEntity> struckEntities = new ArrayList<>();
        struckEntities.add(target);

        LivingEntity currentTarget = target;
        for (int i = 0; i < maxChains; i++) {
            LivingEntity nextTarget = findNextTarget(currentTarget, chainRange, struckEntities, player);
            if (nextTarget != null) {
                playLightningArcAnimation(currentTarget.getEyeLocation(), nextTarget.getEyeLocation());
                nextTarget.getWorld().playSound(nextTarget.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.5f);
                nextTarget.damage(subsequentDamage, player);
                nextTarget.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessDuration, weaknessAmplifier));
                struckEntities.add(nextTarget);
                currentTarget = nextTarget;
            } else {
                break; 
            }
        }

        cooldownManager.setCooldown(player, TrimPattern.BOLT, cooldown);
        sendActivationMessage(player);
    }

    private void playLightningAnimation(Location targetLocation) {
        World world = targetLocation.getWorld();
        if (world == null) return;

        final List<BlockDisplay> lightningParts = new ArrayList<>();
        final Material lightningMaterial = Material.CYAN_WOOL;
        final int lifeTicks = 10; 

        Location startPoint = targetLocation.clone().add(0, 15, 0);
        if (startPoint.getY() > world.getMaxHeight()) {
            startPoint.setY(world.getMaxHeight());
        }

        Vector travel = targetLocation.toVector().subtract(startPoint.toVector());
        int segments = 25;
        Vector step = travel.clone().multiply(1.0 / segments);

        Location current = startPoint.clone();
        for (int i = 0; i < segments; i++) {
            current.add(step);
            Location jittered = current.clone().add(new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).multiply(1.5));

            BlockDisplay part = world.spawn(jittered, BlockDisplay.class, bd -> {
                bd.setBlock(lightningMaterial.createBlockData());
                Transformation t = bd.getTransformation();
                t.getScale().set(0.3f);
                bd.setTransformation(t);
                bd.setBrightness(new BlockDisplay.Brightness(15, 15));
            });
            lightningParts.add(part);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockDisplay part : lightningParts) {
                    part.remove();
                }
            }
        }.runTaskLater(plugin, lifeTicks);

        world.playSound(targetLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
        world.playSound(targetLocation, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 1.0f);
    }

    private void playLightningArcAnimation(Location start, Location end) {
        World world = start.getWorld();
        if (world == null) return;

        final List<BlockDisplay> arcParts = new ArrayList<>();
        final Material lightningMaterial = Material.CYAN_WOOL;
        final int lifeTicks = 8;

        Vector travel = end.toVector().subtract(start.toVector());
        int segments = (int) (travel.length() * 2.0);
        if (segments == 0) return;
        Vector step = travel.clone().multiply(1.0 / segments);

        Location current = start.clone();
        for (int i = 0; i < segments; i++) {
            current.add(step);
            Location jittered = current.clone().add(new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).multiply(0.7));

            BlockDisplay part = world.spawn(jittered, BlockDisplay.class, bd -> {
                bd.setBlock(lightningMaterial.createBlockData());
                Transformation t = bd.getTransformation();
                t.getScale().set(0.25f);
                bd.setTransformation(t);
                bd.setBrightness(new BlockDisplay.Brightness(15, 15));
            });
            arcParts.add(part);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockDisplay part : arcParts) {
                    part.remove();
                }
            }
        }.runTaskLater(plugin, lifeTicks);
    }

    private LivingEntity getTarget(Player player, int range) {
        Collection<Entity> nearbyEntities = player.getNearbyEntities(range, range, range);
        LivingEntity target = null;
        double minAngle = Double.MAX_VALUE;

        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity && player.hasLineOfSight(entity)) {
                if (entity instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue;
                }
                double angle = player.getLocation().getDirection().angle(entity.getLocation().toVector().subtract(player.getLocation().toVector()));
                if (angle < minAngle) {
                    minAngle = angle;
                    target = (LivingEntity) entity;
                }
            }
        }
        if(target != null && minAngle < 0.2){
            return target;
        }
        return null;
    }

    private LivingEntity findNextTarget(LivingEntity from, int range, List<LivingEntity> excluded, Player caster) {
        LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Entity entity : from.getNearbyEntities(range, range, range)) {
            if (entity instanceof LivingEntity && !excluded.contains(entity) && !entity.equals(caster)) {
                 if (entity instanceof Player targetPlayer && trustManager.isTrusted(caster.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue;
                }
                double distSq = entity.getLocation().distanceSquared(from.getLocation());
                if (distSq < closestDistSq) {
                    closest = (LivingEntity) entity;
                    closestDistSq = distSq;
                }
            }
        }
        return closest;
    }

    private void sendActivationMessage(Player player) {
        Component message = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("Bolt", NamedTextColor.YELLOW))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text("You have activated ", NamedTextColor.GRAY))
                .append(Component.text("Chain Lightning", NamedTextColor.YELLOW))
                .append(Component.text("!", NamedTextColor.GRAY));
        player.sendMessage(message);
    }
}
