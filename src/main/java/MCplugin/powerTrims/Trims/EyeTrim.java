package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EyeTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final NamespacedKey effectKey;
    private static final double TRUE_SIGHT_RADIUS = 80.0;
    private static final int TRUE_SIGHT_DURATION = 600; // 30 seconds (in ticks)
    private static final long TRUE_SIGHT_COOLDOWN = 120000; // 2 minutes
    private final Map<UUID, BukkitRunnable> activeTrueSightTasks = new HashMap<>();

    public EyeTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.effectKey = new NamespacedKey(plugin, "eye_trim_effect");
        EyePassive();
    }

    private void EyePassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.EYE)) {
                    if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false, true));
                        player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                    }
                } else {
                    if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                        player.getPersistentDataContainer().remove(effectKey);
                    }
                }
            }
        }, 0L, 20L);
    }

    public void EyePrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.EYE)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.EYE)) return;

        // Cancel existing effect if present
        if (activeTrueSightTasks.containsKey(player.getUniqueId())) {
            activeTrueSightTasks.get(player.getUniqueId()).cancel();
        }

        // Activation effects
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 2.0f);
        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        createEyeEffect(player);

        // Start True Sight task
        BukkitRunnable trueSightTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= TRUE_SIGHT_DURATION || !player.isOnline()) {
                    cancel();
                    activeTrueSightTasks.remove(player.getUniqueId());
                    return;
                }

                if (ticks % 10 == 0) createEyeEffect(player);

                // Highlight entities and apply effects
                for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), TRUE_SIGHT_RADIUS, TRUE_SIGHT_RADIUS, TRUE_SIGHT_RADIUS)) {
                    if (entity instanceof LivingEntity target && !target.equals(player)) {
                        // Remove invisibility
                        if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                            target.removePotionEffect(PotionEffectType.INVISIBILITY);
                        }

                        // Apply the glowing effect
                        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 1200, 0, false, false)); // 1 minute glowing effect

                        // Apply weakness and slowness debuffs (30 seconds)
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 0, false, false)); // 30 seconds
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 1, false, false)); // 30 seconds


                    }
                }


                ticks += 2;
            }
        };

        trueSightTask.runTaskTimer(plugin, 0L, 2L);
        activeTrueSightTasks.put(player.getUniqueId(), trueSightTask);

        cooldownManager.setCooldown(player, TrimPattern.EYE, TRUE_SIGHT_COOLDOWN);
        player.sendMessage("§8[§bEye§8] §7True Sight activated!");
    }

    private void createEyeEffect(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Create swirling dark particles around the player to form the eye
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 15) {
            double x = Math.cos(angle) * TRUE_SIGHT_RADIUS;
            double z = Math.sin(angle) * TRUE_SIGHT_RADIUS;
            Location pLoc = loc.clone().add(x, 0.1, z);

            // Dark smoke effect
            world.spawnParticle(Particle.SMOKE, pLoc, 3, 0, 0, 0, 0.05);

            // Red glowing effect using DUST particle
            world.spawnParticle(Particle.DUST, pLoc, 2, new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.2f));
        }

        // Central glowing effect (the eye itself)
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.2, 0), 20, new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.5f));

        // Add a powerful aura around the eye
        world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1.2, 0), 15, 0.6, 0.6, 0.6, 0.1);
        world.spawnParticle(Particle.LAVA, loc.clone().add(0, 1.2, 0), 15, 0.6, 0.6, 0.6, 0.1);
    }





    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) EyePrimary(player);
    }
}
