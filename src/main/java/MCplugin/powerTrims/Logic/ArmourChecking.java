/*
 * This file is part of [ POWER TRIMS ].
 *
 * [POWER TRIMS] is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * [ POWER TRIMS ] is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with [Your Plugin Name].  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) [2025] [ div ].
 */



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
