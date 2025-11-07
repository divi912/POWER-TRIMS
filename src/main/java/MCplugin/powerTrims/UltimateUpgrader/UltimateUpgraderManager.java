package MCplugin.powerTrims.UltimateUpgrader;

import MCplugin.powerTrims.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UltimateUpgraderManager implements Listener, CommandExecutor, InventoryHolder {

    private final RitualManager ritualManager;
    private final String GUI_NAME = "§5Ultimate Upgrader";
    private final NamespacedKey upgradeKey;

    public static final int HELMET_SLOT = 10;
    public static final int CHESTPLATE_SLOT = 12;
    public static final int LEGGINGS_SLOT = 14;
    public static final int BOOTS_SLOT = 16;
    private static final int UPGRADE_BUTTON_SLOT = 22;
    public static final Set<Integer> ARMOR_SLOTS = new HashSet<>(Arrays.asList(HELMET_SLOT, CHESTPLATE_SLOT, LEGGINGS_SLOT, BOOTS_SLOT));

    public UltimateUpgraderManager(JavaPlugin plugin, RitualManager ritualManager, NamespacedKey upgradeKey, ConfigManager configManager) {
        this.ritualManager = ritualManager;
        this.upgradeKey = upgradeKey;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("upgrade");
        if (command != null) {
            command.setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            if (ritualManager.isPlayerInRitual(player.getUniqueId())) {
                player.sendMessage("§cYou are already performing a ritual!");
                return true;
            }
            openUpgradeGUI(player);
        } else {
            sender.sendMessage("This command can only be used by players.");
        }
        return true;
    }

    private void openUpgradeGUI(Player player) {
        Inventory gui = Bukkit.createInventory(this, 27, GUI_NAME);

        ItemStack placeholder = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, placeholder);
        }

        gui.setItem(HELMET_SLOT, null);
        gui.setItem(CHESTPLATE_SLOT, null);
        gui.setItem(LEGGINGS_SLOT, null);
        gui.setItem(BOOTS_SLOT, null);

        gui.setItem(UPGRADE_BUTTON_SLOT, createGuiItem(Material.AMETHYST_SHARD, "§d§lInitiate Ritual", "§7Place a full set of armor with a", "§7valid trim to begin the ritual."));

        player.openInventory(gui);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof UltimateUpgraderManager)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        int rawSlot = event.getRawSlot();

        if (event.getView().getTopInventory().equals(clickedInventory)) {
            if (ARMOR_SLOTS.contains(rawSlot)) {
                return; // Allow placing/taking armor
            }

            // Prevent shift-clicking items into the GUI from the player's inventory
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }

            if (rawSlot == UPGRADE_BUTTON_SLOT) {
                event.setCancelled(true);
                initiateRitual(player, event.getView().getTopInventory());
                return;
            }

            // Cancel all other clicks in the top inventory
            event.setCancelled(true);
        } else if (event.isShiftClick() && clickedInventory != null && clickedInventory.equals(event.getView().getBottomInventory())) {
            // Also explicitly cancel shift-clicks from the bottom inventory
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSmithing(PrepareSmithingEvent event) {
        SmithingInventory inventory = event.getInventory();
        if (inventory == null) return;

        ItemStack armorPiece = inventory.getItem(1);

        if (armorPiece == null || armorPiece.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = armorPiece.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(upgradeKey, PersistentDataType.BYTE)) {
            event.setResult(null);
        }
    }

    private void initiateRitual(Player player, Inventory gui) {
        ItemStack[] armorSet = {gui.getItem(HELMET_SLOT), gui.getItem(CHESTPLATE_SLOT), gui.getItem(LEGGINGS_SLOT), gui.getItem(BOOTS_SLOT)};

        TrimPattern pattern = null;
        for (ItemStack item : armorSet) {
            if (item == null || !(item.getItemMeta() instanceof ArmorMeta armorMeta)) {
                player.sendMessage("§cPlease place a full, trimmed armor set in the slots.");
                return;
            }

            if (armorMeta.getPersistentDataContainer().has(upgradeKey, PersistentDataType.BYTE)) {
                player.sendMessage("§cThis armor piece has already been upgraded and cannot be modified further.");
                return;
            }

            ArmorTrim trim = armorMeta.getTrim();
            if (trim == null) {
                player.sendMessage("§cAn armor piece is missing a trim.");
                return;
            }
            if (pattern == null) {
                pattern = trim.getPattern();
            } else if (!pattern.equals(trim.getPattern())) {
                player.sendMessage("§cAll armor pieces must have the same trim pattern.");
                return;
            }
        }

        RitualConfig config = ritualManager.getRitualConfig(pattern);
        if (config == null) {
            player.sendMessage("§cThere is no upgrade ritual for this trim pattern.");
            return;
        }

        if (!config.isEnabled()) {
            player.sendMessage("§cThe upgrade ritual for this trim pattern is currently disabled.");
            return;
        }

        if (config.getLimit() >= 0 && ritualManager.getUpgradeCount(pattern) >= config.getLimit()) {
            player.sendMessage("§cThe maximum number of upgrades for this armor set has been reached.");
            return;
        }

        if (!hasRequiredMaterials(player, config.getMaterials())) {
            player.sendMessage("§cYou lack the required materials for this ritual.");
            return;
        }

        // Pass responsibility to the RitualManager to handle items atomically
        ritualManager.startRitual(player, armorSet, config, gui);

        player.closeInventory();
        player.sendMessage("§aThe ritual has begun!");
    }

    private boolean hasRequiredMaterials(Player player, List<ItemStack> materials) {
        for (ItemStack material : materials) {
            int requiredAmount = material.getAmount();
            int foundAmount = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material.getType()) {
                    foundAmount += item.getAmount();
                }
            }
            if (foundAmount < requiredAmount) {
                return false;
            }
        }
        return true;
    }

    public void consumeMaterials(Player player, List<ItemStack> materials) {
        for (ItemStack material : materials) {
            player.getInventory().removeItem(material);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Only act if the ritual is NOT in progress for this player
        if (event.getInventory().getHolder() instanceof UltimateUpgraderManager && !ritualManager.isPlayerInRitual(event.getPlayer().getUniqueId())) {
            Inventory gui = event.getInventory();
            for (int slot : ARMOR_SLOTS) {
                ItemStack item = gui.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    event.getPlayer().getInventory().addItem(item);
                }
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
