package MCplugin.powerTrims.Logic;

import com.jeff_media.armorequipevent.ArmorEquipEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
        passiveEffects.put(TrimPattern.SILENCE, List.of(
                new PotionEffect(PotionEffectType.STRENGTH, INFINITE_DURATION, 1, true, false, true)
        ));
        passiveEffects.put(TrimPattern.VEX, List.of(
                new PotionEffect(PotionEffectType.SPEED, INFINITE_DURATION, 1, true, false, true)
        ));
        passiveEffects.put(TrimPattern.SNOUT, List.of(
                new PotionEffect(PotionEffectType.STRENGTH, INFINITE_DURATION, 0, true, false, true)
        ));
        passiveEffects.put(TrimPattern.COAST, List.of(
                new PotionEffect(PotionEffectType.DOLPHINS_GRACE, INFINITE_DURATION, 0, true, false, true)
        ));
        passiveEffects.put(TrimPattern.WILD, List.of(
                new PotionEffect(PotionEffectType.REGENERATION, INFINITE_DURATION, 0, true, false, true)
        ));
        passiveEffects.put(TrimPattern.TIDE, List.of(
                new PotionEffect(PotionEffectType.DOLPHINS_GRACE, INFINITE_DURATION, 2, true, false, true)
        ));
        passiveEffects.put(TrimPattern.DUNE, List.of(
                new PotionEffect(PotionEffectType.HASTE, INFINITE_DURATION, 0, true, false, true),
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, INFINITE_DURATION, 0, true, false, true)
        ));
        passiveEffects.put(TrimPattern.EYE, List.of(
                new PotionEffect(PotionEffectType.NIGHT_VISION, INFINITE_DURATION, 0, true, false, true)
        ));
        passiveEffects.put(TrimPattern.WARD, List.of(
                new PotionEffect(PotionEffectType.RESISTANCE, INFINITE_DURATION, 0, true, false, true)
        ));
        passiveEffects.put(TrimPattern.SENTRY, List.of(
                new PotionEffect(PotionEffectType.RESISTANCE, INFINITE_DURATION, 0, true, false, true)
        ));
        passiveEffects.put(TrimPattern.SPIRE, List.of(
                new PotionEffect(PotionEffectType.SPEED, INFINITE_DURATION, 1, true, false, true)
        ));
        passiveEffects.put(TrimPattern.RIB, List.of(
                new PotionEffect(PotionEffectType.RESISTANCE,INFINITE_DURATION, 0, true, false, true)
        ));
        passiveEffects.put(TrimPattern.BOLT, List.of(
                new PotionEffect(PotionEffectType.SPEED,INFINITE_DURATION, 2, true, false, true)
        ));
        passiveEffects.put(TrimPattern.FLOW, List.of(
                new PotionEffect(PotionEffectType.SPEED, INFINITE_DURATION, 1, true, false, true)
        ));
    }

    /**
     * Removes all passive effects that were applied by this plugin.
     * It checks for metadata to ensure it doesn't remove effects from other sources.
     */
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

    /**
     * Applies trim effects based on the player's currently equipped armor.
     * This method first removes any old effects owned by the plugin, then applies new ones.
     */
    public void applyTrimEffects(Player player) {
        // First, remove any effects this plugin is currently responsible for.
        removeOwnedEffects(player);

        // Now, determine what new effects to apply based on current gear.
        TrimPattern equipped = ArmourChecking.getEquippedTrim(player);

        if (equipped != null && passiveEffects.containsKey(equipped)) {
            String trimName = equipped.getKey().getKey();
            if (configManager.isTrimEnabled(trimName)) {
                for (PotionEffect effect : passiveEffects.get(equipped)) {
                    // Apply the new effect
                    player.addPotionEffect(effect);
                    // And "claim" it by setting metadata so we know to remove it later
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
            // Re-apply effects after a short delay to allow the milk to clear them first
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyTrimEffects(event.getPlayer()), 1L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Delay to ensure the player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTrimEffects(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onArmorEquip(ArmorEquipEvent event) {
        // Delay to ensure the event is fully processed
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTrimEffects(event.getPlayer()), 1L);
    }
}
