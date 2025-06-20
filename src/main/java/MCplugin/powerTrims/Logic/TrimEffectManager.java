package MCplugin.powerTrims.Logic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.jeff_media.armorequipevent.ArmorEquipEvent;

import java.util.*;

import static org.bukkit.potion.PotionEffect.INFINITE_DURATION;

public class TrimEffectManager implements Listener {
    private final JavaPlugin plugin;
    private final Map<TrimPattern, List<PotionEffect>> passiveEffects = new HashMap<>();



    public TrimEffectManager(JavaPlugin plugin) {
        this.plugin = plugin;
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
        passiveEffects.put(TrimPattern.FLOW, List.of(
                new PotionEffect(PotionEffectType.SPEED, INFINITE_DURATION, 1, true, false, true)
        ));
    }

    private void applyTrimEffects(Player player) {
        TrimPattern equipped = ArmourChecking.getEquippedTrim(player); // Get the one matching trim

        // Remove all effects first
        for (List<PotionEffect> effects : passiveEffects.values()) {
            for (PotionEffect effect : effects) {
                player.removePotionEffect(effect.getType());
            }
        }

        // Now apply only the matching one
        if (equipped != null && passiveEffects.containsKey(equipped)) {
            for (PotionEffect effect : passiveEffects.get(equipped)) {
                player.addPotionEffect(effect);
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
