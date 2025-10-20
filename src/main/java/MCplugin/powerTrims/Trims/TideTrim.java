package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.util.Vector;
import java.util.HashSet;
import java.util.Set;

public class TideTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    private final long TIDE_COOLDOWN;
    private final double WAVE_WIDTH;
    private final int EFFECT_DURATION_TICKS;
    private final double KNOCKBACK_STRENGTH;
    private final int WALL_HEIGHT;
    private final int MOVE_DELAY_TICKS;
    private final int MAX_MOVES;

    public TideTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        TIDE_COOLDOWN = configManager.getLong("tide.primary.cooldown");
        WAVE_WIDTH = configManager.getDouble("tide.primary.wave_width");
        EFFECT_DURATION_TICKS = configManager.getInt("tide.primary.effect_duration_ticks");
        KNOCKBACK_STRENGTH = configManager.getDouble("tide.primary.knockback_strength");
        WALL_HEIGHT = configManager.getInt("tide.primary.wall_height");
        MOVE_DELAY_TICKS = configManager.getInt("tide.primary.move_delay_ticks");
        MAX_MOVES = configManager.getInt("tide.primary.max_moves");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        abilityManager.registerPrimaryAbility(TrimPattern.TIDE, this::activateTidePrimary);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);

            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    public void activateTidePrimary(Player player) {
        if (!configManager.isTrimEnabled("tide")) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.TIDE) ||
                cooldownManager.isOnCooldown(player, TrimPattern.TIDE)) {
            return;
        }

        Location startLoc = player.getLocation();
        Vector direction = startLoc.getDirection().setY(0).normalize();
        World world = player.getWorld();

        world.playSound(startLoc, Sound.ENTITY_GENERIC_SPLASH, 2.0f, 0.5f);
        world.playSound(startLoc, Sound.BLOCK_WATER_AMBIENT, 2.0f, 1.0f);

        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());

        new BukkitRunnable() {
            private int moves = 0;
            private final Location currentLoc = startLoc.clone();
            private final Set<Block> previousWaterBlocks = new HashSet<>();
            private final Set<Block> currentWaterBlocks = new HashSet<>();

            @Override
            public void run() {
                clearWaterBlocks(previousWaterBlocks, world);

                previousWaterBlocks.clear();
                previousWaterBlocks.addAll(currentWaterBlocks);
                currentWaterBlocks.clear();

                if (moves >= MAX_MOVES) {
                    clearWaterBlocks(previousWaterBlocks, world);
                    world.playSound(currentLoc, Sound.WEATHER_RAIN, 1.5f, 1.0f);
                    this.cancel();
                    return;
                }

                currentLoc.add(direction);

                for (double w = -WAVE_WIDTH; w <= WAVE_WIDTH; w += 0.8) {
                    Location wallBaseLoc = currentLoc.clone().add(perpendicular.clone().multiply(w));
                    for (int h = 0; h < WALL_HEIGHT; h++) {
                        Block block = wallBaseLoc.clone().add(0, h, 0).getBlock();
                        if (block.getType().isAir()) {
                            block.setType(Material.WATER, false);
                            currentWaterBlocks.add(block);
                        } else if (!block.getType().isSolid()) {
                            block.setType(Material.WATER, false);
                            currentWaterBlocks.add(block);
                        } else {
                            break;
                        }
                    }
                }

                for (LivingEntity target : world.getNearbyLivingEntities(currentLoc, WAVE_WIDTH + 1.5, WALL_HEIGHT, WAVE_WIDTH + 1.5)) {
                    if (target.equals(player)) continue;
                    if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) continue;

                    Vector knockback = direction.clone().multiply(KNOCKBACK_STRENGTH);
                    knockback.setY(0.4);
                    target.setVelocity(knockback);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_DURATION_TICKS, 1));

                    world.spawnParticle(Particle.SPLASH, target.getLocation(), 15, 0.4, 1, 0.4, 0.2);
                    world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.2f);
                }

                world.playSound(currentLoc, Sound.BLOCK_WATER_AMBIENT, 0.8f, 1.2f);
                moves++;
            }
        }.runTaskTimer(plugin, 0L, MOVE_DELAY_TICKS);

        Messaging.sendTrimMessage(player, "Tide", ChatColor.AQUA, "You have summoned a massive tidal wall!");
        cooldownManager.setCooldown(player, TrimPattern.TIDE, TIDE_COOLDOWN);
    }

    private void clearWaterBlocks(Set<Block> blocks, World world) {
        for (Block block : blocks) {
            if (block.getType() == Material.WATER) {
                block.setType(Material.AIR);
                world.spawnParticle(Particle.SPLASH, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }
}