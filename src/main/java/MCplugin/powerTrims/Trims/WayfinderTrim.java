package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WayfinderTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final NamespacedKey effectKey;
    private static final long TELEPORT_COOLDOWN = 120000; // 2 minutes cooldown
    private final Map<UUID, Location> markedLocations = new HashMap<>();

    public WayfinderTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.effectKey = new NamespacedKey(plugin, "wayfinder_trim_effect");
        WayfinderPassive();
    }

    private void WayfinderPassive() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WAYFINDER)) {
                    if (player.isSneaking()) {
                        player.setWalkSpeed(0.6f); // Increased speed while sneaking
                        player.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
                    } else {
                        player.setWalkSpeed(0.2f); // Normal speed when not sneaking
                    }
                } else {
                    if (player.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) {
                        player.setWalkSpeed(0.2f);
                        player.getPersistentDataContainer().remove(effectKey);
                    }
                }
            }
        }, 0L, 10L);
    }

    public void WayfinderPrimary(Player player) {
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.WAYFINDER)) {
            return;
        }
        if (cooldownManager.isOnCooldown(player, TrimPattern.WAYFINDER)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        World world = player.getWorld();

        // Check if a marker already exists for this player
        if (markedLocations.containsKey(uuid)) {
            // Teleport back to the marked location and clear the marker
            Location mark = markedLocations.remove(uuid);
            player.teleport(mark);
            player.sendMessage(ChatColor.AQUA + "You have returned to your marked location!");
            player.playSound(mark, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            // Create a teleportation particle effect visible to all
            world.spawnParticle(Particle.PORTAL, mark, 100, 1, 1, 1, 0.5);
            // Set ability cooldown
            cooldownManager.setCooldown(player, TrimPattern.WAYFINDER, TELEPORT_COOLDOWN);
        } else {
            // Mark current location without teleporting
            Location mark = player.getLocation();
            markedLocations.put(uuid, mark);
            player.sendMessage(ChatColor.DARK_AQUA + "You have marked this location!");
            player.playSound(mark, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, mark, 50, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getNewSlot() == 8) {
            WayfinderPrimary(player);
        }
    }
}
