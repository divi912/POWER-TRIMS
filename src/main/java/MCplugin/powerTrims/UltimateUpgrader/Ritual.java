package MCplugin.powerTrims.UltimateUpgrader;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

public class Ritual {

    private final JavaPlugin plugin;
    private final Player player;
    private final Location location;
    private final ItemStack[] armorSet;
    private final RitualConfig config;
    private final RitualManager ritualManager;
    private final NamespacedKey upgradeKey;
    private final RitualAnimation animation;

    public Ritual(JavaPlugin plugin, Player player, Location location, ItemStack[] armorSet, RitualConfig config, RitualManager ritualManager, NamespacedKey upgradeKey) {
        this.plugin = plugin;
        this.player = player;
        this.location = location;
        this.armorSet = armorSet;
        this.config = config;
        this.ritualManager = ritualManager;
        this.upgradeKey = upgradeKey;
        this.animation = new RitualAnimation(plugin, location, armorSet, config.getMaterials());
    }

    public void start() {
        animation.start();
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);

        new BukkitRunnable() {
            int countdown = config.getDuration();

            @Override
            public void run() {
                if (countdown <= 0) {
                    complete();
                    this.cancel();
                    return;
                }

                animation.updateHologram(countdown);
                location.getWorld().playSound(location, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.5f, 1.5f);

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void complete() {
        animation.stop();

        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 1.0f);

        TrimPattern pattern = null;

        for (ItemStack item : armorSet) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (pattern == null && meta instanceof ArmorMeta) {
                    ArmorTrim trim = ((ArmorMeta) meta).getTrim();
                    if (trim != null) {
                        pattern = trim.getPattern();
                    }
                }

                meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.BYTE, (byte) 1);

                List<String> newLore = Arrays.asList(
                        "§7A relic of a forgotten age,",
                        "§7pulsating with untold power.",
                        "",
                        "§d§lMYTHIC ARMOR"
                );

                meta.setLore(newLore);
                item.setItemMeta(meta);
                location.getWorld().dropItemNaturally(location, item);
            }
        }

        if (pattern != null) {
            ritualManager.incrementUpgradeCount(pattern);
        }

        ritualManager.endRitual(player.getUniqueId());
        player.sendMessage("§aThe ritual is complete! Your armor has been empowered.");
    }
}
