package MCplugin.powerTrims.Logic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DoubleSneakManager implements Listener {

    private final JavaPlugin plugin;
    private final AbilityManager abilityManager;
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();
    private static final long DOUBLE_SNEAK_THRESHOLD = 500; // milliseconds
    private final boolean geyserApiAvailable;

    public DoubleSneakManager(JavaPlugin plugin, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;

        // Safely check if the Geyser plugin is on the server
        this.geyserApiAvailable = Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null;

        if (geyserApiAvailable) {
            plugin.getLogger().info("Geyser-Spigot detected, Bedrock double-sneak support enabled.");
        } else {
            plugin.getLogger().info("Geyser-Spigot not found, Bedrock double-sneak support disabled.");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        // If Geyser isn't available, this manager should do nothing.
        if (!geyserApiAvailable) {
            return;
        }

        // To avoid the NoClassDefFoundError, we put the Geyser-specific logic
        // in a separate method. This ensures the Geyser API classes are only
        // referenced when we are sure they exist.
        handleBedrockSneak(event.getPlayer(), event.isSneaking());
    }

    private void handleBedrockSneak(Player player, boolean isSneaking) {
        // This method is only ever called if the Geyser API is available.
        if (!org.geysermc.geyser.api.GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
            return;
        }

        if (isSneaking) {
            UUID playerUuid = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            if (lastSneakTime.containsKey(playerUuid)) {
                long timeSinceLastSneak = currentTime - lastSneakTime.get(playerUuid);
                if (timeSinceLastSneak < DOUBLE_SNEAK_THRESHOLD) {
                    abilityManager.activatePrimaryAbility(player);
                }
            }
            lastSneakTime.put(playerUuid, currentTime);
        }
    }
}
