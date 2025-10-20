package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WayfinderTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final NamespacedKey effectKey;
    private final long TELEPORT_COOLDOWN;
    private final Map<UUID, Location> markedLocations = new HashMap<>();

    public WayfinderTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;
        this.effectKey = new NamespacedKey(plugin, "wayfinder_trim_effect");
        this.TELEPORT_COOLDOWN = configManager.getLong("wayfinder.primary.cooldown");

        abilityManager.registerPrimaryAbility(TrimPattern.WAYFINDER, this::WayfinderPrimary);
    }



    public void WayfinderPrimary(Player player) {
        if (!configManager.isTrimEnabled("wayfinder")) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WAYFINDER)) {
            return;
        }
        if (cooldownManager.isOnCooldown(player, TrimPattern.WAYFINDER)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        World world = player.getWorld();

        if (markedLocations.containsKey(uuid)) {
            Location mark = markedLocations.remove(uuid);
            playTeleportAnimation(player.getLocation(), mark);
            player.teleport(mark);
            Messaging.sendTrimMessage(player, "Wayfinder", ChatColor.AQUA, "You have returned to your marked location!");
            player.playSound(mark, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            cooldownManager.setCooldown(player, TrimPattern.WAYFINDER, TELEPORT_COOLDOWN);
        } else {
            Location mark = player.getLocation();
            markedLocations.put(uuid, mark);
            playMarkAnimation(mark);
            Messaging.sendTrimMessage(player, "Wayfinder", ChatColor.DARK_AQUA, "You have marked this location!");
            player.playSound(mark, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        }
    }

    private void playMarkAnimation(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        final List<BlockDisplay> blocks = new ArrayList<>();
        final Material markMaterial = Material.EMERALD_BLOCK;
        final int durationTicks = 30;

        BlockDisplay centerBlock = world.spawn(location.clone().add(0, 0.5, 0), BlockDisplay.class, bd -> {
            bd.setBlock(markMaterial.createBlockData());
            Transformation t = bd.getTransformation();
            t.getScale().set(0.01f);
            bd.setTransformation(t);
            bd.setBrightness(new BlockDisplay.Brightness(15, 15));
        });
        blocks.add(centerBlock);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > durationTicks) {
                    blocks.forEach(Entity::remove);
                    this.cancel();
                    return;
                }
                float progress = (float) ticks / durationTicks;
                float scale = (float) (Math.sin(progress * Math.PI) * 0.6f);
                Transformation t = centerBlock.getTransformation();
                t.getScale().set(scale);
                centerBlock.setTransformation(t);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playTeleportAnimation(Location from, Location to) {
        playVortexAnimation(from, Material.PURPUR_BLOCK);
        playVortexAnimation(to, Material.EMERALD_BLOCK);
    }

    private void playVortexAnimation(Location location, Material material) {
        World world = location.getWorld();
        if (world == null) return;

        final List<BlockDisplay> blocks = new ArrayList<>();
        final int durationTicks = 25;
        final int blockCount = 30;

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > durationTicks) {
                    blocks.forEach(Entity::remove);
                    this.cancel();
                    return;
                }

                for (int i = 0; i < 2; i++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double radius = Math.random() * 2.0;
                    Location spawnLoc = location.clone().add(Math.cos(angle) * radius, Math.random() * 2.5, Math.sin(angle) * radius);
                    BlockDisplay block = world.spawn(spawnLoc, BlockDisplay.class, bd -> {
                        bd.setBlock(material.createBlockData());
                        Transformation t = bd.getTransformation();
                        t.getScale().set(0.3f);
                        bd.setTransformation(t);
                    });
                    blocks.add(block);
                }

                blocks.removeIf(block -> {
                    if (!block.isValid()) return true;
                    Vector toCenter = location.toVector().subtract(block.getLocation().toVector()).normalize().multiply(0.25);
                    block.teleport(block.getLocation().add(toCenter));
                    if (block.getLocation().distanceSquared(location) < 1.0) {
                        block.remove();
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (!configManager.isTrimEnabled("wayfinder")) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);

            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }
}
