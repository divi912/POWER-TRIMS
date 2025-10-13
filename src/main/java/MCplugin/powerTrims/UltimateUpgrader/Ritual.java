package MCplugin.powerTrims.UltimateUpgrader;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final List<ItemStack> requiredMaterials;
    private final int duration;
    private final RitualManager ritualManager;
    private final NamespacedKey upgradeKey;
    private final RitualAnimation animation;

    public Ritual(JavaPlugin plugin, Player player, Location location, ItemStack[] armorSet, List<ItemStack> requiredMaterials, int duration, RitualManager ritualManager, NamespacedKey upgradeKey) {
        this.plugin = plugin;
        this.player = player;
        this.location = location;
        this.armorSet = armorSet;
        this.requiredMaterials = requiredMaterials;
        this.duration = duration;
        this.ritualManager = ritualManager;
        this.upgradeKey = upgradeKey;
        this.animation = new RitualAnimation(plugin, location, armorSet, requiredMaterials);
    }

    public void start() {
        animation.start();
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);

        new BukkitRunnable() {
            int countdown = duration;

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

        for (ItemStack item : armorSet) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.BYTE, (byte) 1);
                meta.setLore(Arrays.asList("§dUltimate Power Unlocked"));
                item.setItemMeta(meta);
                location.getWorld().dropItemNaturally(location, item);
            }
        }

        ritualManager.endRitual(player.getUniqueId());
        player.sendMessage("§aThe ritual is complete! Your armor has been empowered.");
    }
}
