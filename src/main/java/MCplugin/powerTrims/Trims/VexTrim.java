package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;

import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class VexTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final Map<UUID, Long> passiveCooldowns = new HashMap<>();
    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private final long PRIMARY_COOLDOWN;
    private final double PRIMARY_RADIUS;
    private final double PRIMARY_DAMAGE;
    private final int PRIMARY_DEBUFF_DURATION;
    private final int PRIMARY_BLINDNESS_DURATION;
    private final long PASSIVE_COOLDOWN;
    private final long PASSIVE_HIDE_DURATION_TICKS;
    private final double PASSIVE_HEALTH_THRESHOLD;

    public VexTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PRIMARY_COOLDOWN = configManager.getLong("vex.primary.cooldown");
        PRIMARY_RADIUS = configManager.getDouble("vex.primary.radius");
        PRIMARY_DAMAGE = configManager.getDouble("vex.primary.damage");
        PRIMARY_DEBUFF_DURATION = configManager.getInt("vex.primary.debuff_duration_ticks");
        PRIMARY_BLINDNESS_DURATION = configManager.getInt("vex.primary.blindness_duration_ticks");
        PASSIVE_COOLDOWN = configManager.getLong("vex.passive.cooldown");
        PASSIVE_HIDE_DURATION_TICKS = configManager.getLong("vex.passive.hide_duration_ticks");
        PASSIVE_HEALTH_THRESHOLD = configManager.getDouble("vex.passive.health_threshold");

        abilityManager.registerPrimaryAbility(TrimPattern.VEX, this::VexPrimary);
    }


    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void VexPrimary(Player player) {
        if (!configManager.isTrimEnabled("vex")) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.VEX)) return;

        final double radius = PRIMARY_RADIUS;
        final int steps = 30;
        final long interval = 2L;
        final int points = 72;

        Location center = player.getLocation().clone().add(0, 0.1, 0);

        player.getWorld().playSound(center, Sound.ENTITY_VEX_CHARGE, 1.0f, 1.0f);

        new BukkitRunnable() {
            int i = 1;
            @Override
            public void run() {
                double r = (radius / steps) * i;
                double y = center.getY();
                for (int j = 0; j < 360; j += 360 / points) {
                    double rad = Math.toRadians(j);
                    double x = center.getX() + r * Math.cos(rad);
                    double z = center.getZ() + r * Math.sin(rad);
                    center.getWorld().spawnParticle(
                            Particle.WITCH,
                            x, y, z,
                            3,
                            0.1, 0.1, 0.1,
                            0,
                            null
                    );
                }
                if (++i > steps) {
                    this.cancel();
                    for (LivingEntity target : center.getWorld().getNearbyLivingEntities(center, radius, radius, radius)) {
                        if (target.equals(player)) continue;
                        if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                            continue;
                        }
                        target.damage(PRIMARY_DAMAGE, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, PRIMARY_DEBUFF_DURATION, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PRIMARY_DEBUFF_DURATION, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PRIMARY_DEBUFF_DURATION, 0));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PRIMARY_BLINDNESS_DURATION, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, interval);

        cooldownManager.setCooldown(player, TrimPattern.VEX, PRIMARY_COOLDOWN);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) {

            double newHealth = player.getHealth() - event.getFinalDamage();
            if (newHealth <= 0) return;
            if (newHealth >= PASSIVE_HEALTH_THRESHOLD) return;
            if (isPassiveOnCooldown(player)) return;
            activatePassiveAbility(player);
        }
    }

    private void activatePassiveAbility(Player player) {
        passiveCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + PASSIVE_COOLDOWN);
        hiddenPlayers.add(player.getUniqueId());
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.hidePlayer(plugin, player);
            }
        }

        Messaging.sendTrimMessage(player, "Vex", ChatColor.DARK_GRAY, "You have become invisible for " + (PASSIVE_HIDE_DURATION_TICKS / 20) + " seconds!");

        new BukkitRunnable() {
            @Override
            public void run() {
                hiddenPlayers.remove(player.getUniqueId());
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.showPlayer(plugin, player);
                }
                Messaging.sendTrimMessage(player, "Vex", ChatColor.GREEN, "You are now visible again!");
            }
        }.runTaskLater(plugin, PASSIVE_HIDE_DURATION_TICKS);
    }

    private boolean isPassiveOnCooldown(Player player) {
        return passiveCooldowns.containsKey(player.getUniqueId()) &&
                passiveCooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
    }

    public void cleanup() {
        for (UUID playerUUID : hiddenPlayers) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.showPlayer(plugin, player);
                }
            }
        }
        hiddenPlayers.clear();
    }
}
