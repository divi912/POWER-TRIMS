package MCplugin.powerTrims.ultimates.silenceult;

import MCplugin.powerTrims.Logic.ArmourChecking;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SilenceUlt implements Listener {

    private final JavaPlugin plugin;
    private final SilenceUltData data;
    private final SilenceTransformAnimations animations;
    private final SilenceUltAttacks attacks;
    private final NamespacedKey upgradeKey;

    public SilenceUlt(JavaPlugin plugin, NamespacedKey upgradeKey, SilenceUltData data) {
        this.plugin = plugin;
        this.data = data;
        this.animations = new SilenceTransformAnimations(plugin, this, data);
        this.attacks = new SilenceUltAttacks(plugin, this, data);
        this.upgradeKey = upgradeKey;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cleanupPlayer(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (DisguiseAPI.isDisguised(player)) {
            revertFromWarden(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // Player being damaged
        if (event.getEntity() instanceof Player player) {
            if (ArmourChecking.hasFullUpgradedArmor(player, TrimPattern.SILENCE, upgradeKey)) {
                addRage(player, data.RAGE_PER_HIT_TAKEN);
            }
        }
        // Player dealing damage
        if (event.getDamager() instanceof Player player && ArmourChecking.hasFullUpgradedArmor(player, TrimPattern.SILENCE, upgradeKey)) {
            addRage(player, data.RAGE_PER_HIT_DEALT);
        }
    }

    @EventHandler
    public void onAbilityRelatedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (data.transformingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && data.leapingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || data.leapingPlayers.contains(player.getUniqueId())) return;

        int newSlot = event.getNewSlot();
        boolean isWarden = DisguiseAPI.isDisguised(player) && DisguiseAPI.getDisguise(player).getType() == DisguiseType.WARDEN;

        switch (newSlot) {
            case 0 -> {
                double currentRage = data.rage.getOrDefault(player.getUniqueId(), 0.0);
                if (currentRage >= data.MAX_RAGE && !isWarden && !data.transformingPlayers.contains(player.getUniqueId())) {
                    animations.startTransformationSequence(player);
                }
            }
            case 6 -> { if (isWarden) attacks.tryUseWardenBoom(player); }
            case 7 -> { if (isWarden) attacks.tryUseDeepDarkGrasp(player); }
            case 8 -> { if (isWarden) attacks.tryUseObliteratingLeap(player); }
        }
    }

    public void addRage(Player player, double amount) {
        if (DisguiseAPI.isDisguised(player) || data.transformingPlayers.contains(player.getUniqueId())) return;
        double currentRage = data.rage.getOrDefault(player.getUniqueId(), 0.0);
        double newRage = Math.min(data.MAX_RAGE, currentRage + amount);
        data.rage.put(player.getUniqueId(), newRage);
    }

    public void completeWardenTransformation(Player player) {
        if (!player.isOnline()) return;
        cleanupPlayer(player.getUniqueId());
        Location loc = player.getLocation();
        World world = loc.getWorld();

        UUID playerUUID = player.getUniqueId();
        world.playSound(loc, Sound.ENTITY_WARDEN_ROAR, 3.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1, 0), 1);
        world.spawnParticle(Particle.EXPLOSION, loc, 5);

        for (Entity entity : world.getNearbyEntities(loc, 10.0, 10.0, 10.0)) {
            if (entity.equals(player) || !(entity instanceof LivingEntity)) continue;
            org.bukkit.util.Vector direction = entity.getLocation().toVector().subtract(loc.toVector()).normalize();
            direction.multiply(1.5).setY(0.5);
            entity.setVelocity(direction);
        }

        for (Entity entity : world.getNearbyEntities(loc, 16, 16, 16)) {
            if (entity instanceof Player nearbyPlayer) {
                nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
            }
        }
        player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "You have embraced the deep dark!");

        MobDisguise wardenDisguise = new MobDisguise(DisguiseType.WARDEN);
        wardenDisguise.setViewSelfDisguise(true);
        wardenDisguise.setTallSelfDisguise(DisguiseConfig.TallSelfDisguise.HIDDEN);
        DisguiseConfig.setNotifyBar(DisguiseConfig.NotifyBar.NONE);
        DisguiseAPI.disguiseToAll(player, wardenDisguise);

        long durationTicks = data.WARDEN_DURATION_SECONDS * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, (int) durationTicks, data.WARDEN_HEALTH_BOOST_LEVEL, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int) durationTicks, data.WARDEN_STRENGTH_LEVEL, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int) durationTicks, data.WARDEN_RESISTANCE_LEVEL, false, false));
        startWardenTimer(player);
    }

    public void revertFromWarden(Player player, boolean sendMessage) {
        cleanupPlayer(player.getUniqueId());
        if (sendMessage) {
            player.sendMessage(ChatColor.GRAY + "The Warden's power recedes...");
        }
        if (DisguiseAPI.isDisguised(player)) DisguiseAPI.undisguiseToAll(player);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.HEALTH_BOOST);
    }

    public void startWardenTimer(Player player) {
        UUID playerUUID = player.getUniqueId();
        long transformEndTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(data.WARDEN_DURATION_SECONDS);
        data.wardenEndTimes.put(playerUUID, transformEndTime);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !data.wardenEndTimes.containsKey(playerUUID)) {
                    revertFromWarden(player, true);
                    this.cancel();
                    return;
                }
                long endTime = data.wardenEndTimes.getOrDefault(playerUUID, 0L);
                if (System.currentTimeMillis() > endTime) {
                    revertFromWarden(player, true);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        data.wardenTimers.put(player.getUniqueId(), task);
    }

    public String getUltimateActionbarString(Player player) {
        UUID playerUUID = player.getUniqueId();
        boolean isWarden = DisguiseAPI.isDisguised(player) && DisguiseAPI.getDisguise(player).getType() == DisguiseType.WARDEN;

        if (isWarden) {
            long now = System.currentTimeMillis();
            long wardenEndTime = data.wardenEndTimes.getOrDefault(playerUUID, 0L);
            long durationLeft = wardenEndTime - now;

            String durationStr = ChatColor.DARK_PURPLE + "Warden: " + ChatColor.LIGHT_PURPLE + (durationLeft > 0 ? (durationLeft / 1000 + 1) + "s" : "Ended");
            String boomStr = getCooldownString("A1", data.BOOM_COOLDOWN_SECONDS, data.wardenBoomCooldowns.get(playerUUID), now);
            String graspStr = getCooldownString("A2", data.GRASP_COOLDOWN_SECONDS, data.deepDarkGraspCooldowns.get(playerUUID), now);
            String leapStr = getCooldownString("A3", data.LEAP_COOLDOWN_SECONDS, data.obliteratingLeapCooldowns.get(playerUUID), now);

            return durationStr + ChatColor.DARK_GRAY + " | " + boomStr + ChatColor.DARK_GRAY + " | " + graspStr + ChatColor.DARK_GRAY + " | " + leapStr;
        } else if (ArmourChecking.hasFullUpgradedArmor(player, TrimPattern.SILENCE, upgradeKey)) {
            double currentRage = data.rage.getOrDefault(playerUUID, 0.0);
            if (currentRage >= data.MAX_RAGE) {
                return ChatColor.RED + "" + ChatColor.BOLD + "ULTIMATE READY";
            } else {
                int ragePercent = (int) ((currentRage / data.MAX_RAGE) * 100);
                return ChatColor.DARK_AQUA + "Rage: " + ChatColor.AQUA + ragePercent + "%";
            }
        }
        return "";
    }

    private String getCooldownString(String abilityName, long totalCooldown, Long lastUsed, long now) {
        if (lastUsed == null) {
            return ChatColor.GREEN + abilityName + ": READY";
        }
        long cooldownMillis = TimeUnit.SECONDS.toMillis(totalCooldown);
        long cooldownLeft = lastUsed + cooldownMillis - now;
        if (cooldownLeft > 0) {
            return ChatColor.YELLOW + abilityName + ": " + ChatColor.RED + String.format("%.1f", cooldownLeft / 1000.0) + "s";
        } else {
            return ChatColor.GREEN + abilityName + ": READY";
        }
    }

    public void cleanupPlayer(UUID playerUUID) {
        animations.revertSculkBlocks();
        data.rage.remove(playerUUID);
        data.wardenBoomCooldowns.remove(playerUUID);
        data.deepDarkGraspCooldowns.remove(playerUUID);
        data.obliteratingLeapCooldowns.remove(playerUUID);
        data.wardenEndTimes.remove(playerUUID);
        data.leapingPlayers.remove(playerUUID);
        data.originalBlocks.remove(playerUUID);
        if (data.sculkTasks.containsKey(playerUUID)) data.sculkTasks.remove(playerUUID).cancel();
        if (data.mainAnimationTasks.containsKey(playerUUID)) data.mainAnimationTasks.remove(playerUUID).cancel();

        if (data.transformingPlayers.remove(playerUUID)) {
            Player p = Bukkit.getPlayer(playerUUID);
            if (p != null && data.ENABLE_WEATHER_EFFECT) p.resetPlayerWeather();
        }
        if (data.wardenTimers.containsKey(playerUUID)) {
            data.wardenTimers.remove(playerUUID).cancel();
        }
    }

    public void revertAllSculkBlocks() {
        animations.revertSculkBlocks();
    }
}
