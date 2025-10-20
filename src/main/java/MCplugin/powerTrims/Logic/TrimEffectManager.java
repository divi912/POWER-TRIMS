package MCplugin.powerTrims.Logic;

import MCplugin.powerTrims.config.ConfigManager;
import com.jeff_media.armorequipevent.ArmorEquipEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bukkit.potion.PotionEffect.INFINITE_DURATION;

public class TrimEffectManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<TrimPattern, List<PotionEffect>> passiveEffects = new HashMap<>();

    public TrimEffectManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerPassiveEffects();
    }

    private void registerPassiveEffects() {
        ConfigurationSection passiveEffectsSection = configManager.getConfig().getConfigurationSection("passive_effects");
        if (passiveEffectsSection == null) return;

        for (String trimName : passiveEffectsSection.getKeys(false)) {
            TrimPattern pattern = getTrimPatternFromString(trimName);
            if (pattern == null) {
                plugin.getLogger().warning("Invalid trim pattern in passive_effects: " + trimName);
                continue;
            }

            List<PotionEffect> effects = new ArrayList<>();
            List<String> effectStrings = passiveEffectsSection.getStringList(trimName);

            if (effectStrings.isEmpty() || (effectStrings.size() == 1 && effectStrings.get(0).equalsIgnoreCase("none"))) {
                passiveEffects.put(pattern, new ArrayList<>()); // No effects for this trim
                continue;
            }

            for (String effectString : effectStrings) {
                try {
                    String[] parts = effectString.split(":");
                    if (parts.length != 2) {
                        plugin.getLogger().warning("Invalid effect format in passive_effects for trim '" + trimName + "': " + effectString);
                        continue;
                    }
                    PotionEffectType effectType = PotionEffectType.getByName(parts[0].toUpperCase());
                    int amplifier = Integer.parseInt(parts[1]);

                    if (effectType == null) {
                        plugin.getLogger().warning("Invalid potion effect type '" + parts[0] + "' for trim '" + trimName + "'.");
                        continue;
                    }

                    effects.add(new PotionEffect(effectType, INFINITE_DURATION, amplifier, true, false, true));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid amplifier in passive_effects for trim '" + trimName + "': " + effectString);
                }
            }
            passiveEffects.put(pattern, effects);
        }
    }

    private TrimPattern getTrimPatternFromString(String patternName) {
        for (TrimPattern pattern : Registry.TRIM_PATTERN) {
            if (pattern.getKey().getKey().equalsIgnoreCase(patternName)) {
                return pattern;
            }
        }
        return null;
    }

    private void removeOwnedEffects(Player player) {
        for (List<PotionEffect> effects : passiveEffects.values()) {
            for (PotionEffect effect : effects) {
                String metadataKey = "powertrims-effect-" + effect.getType().getName();
                if (player.hasMetadata(metadataKey)) {
                    player.removePotionEffect(effect.getType());
                    player.removeMetadata(metadataKey, plugin);
                }
            }
        }
    }

    public void applyTrimEffects(Player player) {
        removeOwnedEffects(player);

        TrimPattern equipped = ArmourChecking.getEquippedTrim(player);

        if (equipped != null && passiveEffects.containsKey(equipped)) {
            String trimName = equipped.getKey().getKey();
            if (configManager.isTrimEnabled(trimName)) {
                for (PotionEffect effect : passiveEffects.get(equipped)) {
                    player.addPotionEffect(effect);
                    String metadataKey = "powertrims-effect-" + effect.getType().getName();
                    player.setMetadata(metadataKey, new FixedMetadataValue(plugin, true));
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTrimEffects(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onMilkDrink(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyTrimEffects(event.getPlayer()), 1L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTrimEffects(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onArmorEquip(ArmorEquipEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTrimEffects(event.getPlayer()), 1L);
    }
}
