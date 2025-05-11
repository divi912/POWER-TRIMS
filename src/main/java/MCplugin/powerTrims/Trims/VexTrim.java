package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.TrimCooldownManager;

import org.bukkit.*;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class VexTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_TIME = 120000; // 20 seconds cooldown
    private static final long HIDE_DURATION = 10000; // 10 seconds hide duration
    private static VexTrim instance;

    private final Set<BukkitRunnable> activeTasks = new HashSet<>(); // Track active tasks

    private BukkitRunnable passiveTask;

    public VexTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        instance = this;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        VexPassive();
    }

    public static VexTrim getInstance() {
        return instance;
    }

    private void VexPassive() {
        if (passiveTask != null) {
            passiveTask.cancel();
        }
        
        passiveTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        NamespacedKey effectKey = new NamespacedKey(plugin, "vex_trim_effect");
                        if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) {
                            if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false, true));
                                player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 2);
                            }
                        } else if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                            player.removePotionEffect(PotionEffectType.SPEED);
                            player.getPersistentDataContainer().remove(effectKey);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in VexPassive for player " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
        };
        passiveTask.runTaskTimer(plugin, 0L, 20L);
        activeTasks.add(passiveTask);
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            activateVexsVengeance(player);
        }
    }

    private void activateVexsVengeance(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.VEX)) return;

        // Ability parameters
        final double radius = 30.0;
        final int steps = 30;
        final long interval = 2L;
        final int points = 72;

        Location center = player.getLocation().clone().add(0, 0.1, 0);
        Particle.DustOptions dust = new Particle.DustOptions(Color.PURPLE, 1.0f);

        // Play sound
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
                    // After effect, apply damage and debuffs
                    for (LivingEntity target : center.getWorld().getNearbyLivingEntities(center, radius, radius, radius)) {
                        if (target.equals(player)) continue;
                        target.damage(8.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, interval);

        cooldownManager.setCooldown(player, TrimPattern.VEX, COOLDOWN_TIME);
        player.sendMessage("§8[§cVex§8] §7Vex's Vengeance unleashed in a 30-block radius!");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.VEX)) {

            // Check if health after damage is BELOW 4 hearts (8 HP)
            double newHealth = player.getHealth() - event.getFinalDamage();
            if (newHealth <= 0) return; // Prevent triggering on death
            if (newHealth >= 8) return; // Ensure ability activates below 4 hearts

            // Check if ability is on cooldown
            if (isOnCooldown(player)) return;

            // Activate ability
            activateAbility(player);
        }
    }

    private void activateAbility(Player player) {
        // Set the cooldown FIRST
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + COOLDOWN_TIME);

        // Hide the player from others
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.hidePlayer(plugin, player);
            }
        }

        player.sendMessage(ChatColor.DARK_GRAY + "You have become invisible for 10 seconds!");

        // Reveal the player after 10 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.showPlayer(plugin, player);
                }
                player.sendMessage(ChatColor.GREEN + "You are now visible again!");
            }
        }.runTaskLater(plugin, HIDE_DURATION / 50); // Convert milliseconds to ticks
    }

    private boolean isOnCooldown(Player player) {
        return cooldowns.containsKey(player.getUniqueId()) &&
                cooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
    }
}



