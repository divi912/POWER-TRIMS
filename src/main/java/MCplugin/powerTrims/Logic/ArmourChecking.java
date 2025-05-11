package MCplugin.powerTrims.Logic;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimPattern;

public class ArmourChecking {
    public static boolean hasFullTrimmedArmor(Player player, TrimPattern pattern) {
        ItemStack[] armor = player.getInventory().getArmorContents();

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType() == Material.AIR) {
                return false; // Missing armor piece
            }

            if (!(piece.getItemMeta() instanceof ArmorMeta armorMeta)) {
                return false; // Not an armor piece with trim support
            }

            ArmorTrim trim = armorMeta.getTrim();
            if (trim == null || trim.getPattern() != pattern) {
                return false; // Missing or incorrect trim pattern
            }
        }
        return true; // All armor pieces have the specified trim pattern
    }

    public static TrimPattern getEquippedTrim(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();

        TrimPattern equippedTrim = null;
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType() == Material.AIR) {
                return null; // Missing armor piece
            }

            if (!(piece.getItemMeta() instanceof ArmorMeta armorMeta)) {
                return null; // Not armor or doesn't support trim
            }

            ArmorTrim trim = armorMeta.getTrim();
            if (trim == null) {
                return null; // No trim found
            }

            if (equippedTrim == null) {
                equippedTrim = trim.getPattern(); // Set initial trim pattern
            } else if (equippedTrim != trim.getPattern()) {
                return null; // Different trims detected, invalid set
            }
        }
        return equippedTrim; // Return the trim pattern if all match
    }

}
