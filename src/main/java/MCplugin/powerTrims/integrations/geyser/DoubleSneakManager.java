package MCplugin.powerTrims.integrations.geyser;

import MCplugin.powerTrims.Logic.AbilityManager;
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
    private final GeyserIntegration geyserIntegration;

    public DoubleSneakManager(JavaPlugin plugin, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;

        if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
            this.geyserIntegration = new GeyserIntegrationImpl();
            plugin.getLogger().info("Geyser-Spigot detected, Bedrock double-sneak support enabled.");
        } else {
            this.geyserIntegration = new NoGeyserIntegration();
            plugin.getLogger().info("Geyser-Spigot not found, Bedrock double-sneak support disabled.");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!geyserIntegration.isBedrockPlayer(player.getUniqueId())) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (event.isSneaking()) {
            lastSneakTime.put(playerUuid, currentTime);
        } else {
            long lastTime = lastSneakTime.getOrDefault(playerUuid, 0L);
            if (currentTime - lastTime < DOUBLE_SNEAK_THRESHOLD) {
                abilityManager.activatePrimaryAbility(player);
                lastSneakTime.put(playerUuid, 0L);
            }
        }
    }
}
