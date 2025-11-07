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

package MCplugin.powerTrims.integrations;

import MCplugin.powerTrims.PowerTrimss;
import MCplugin.powerTrims.Logic.ArmourChecking;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.NotNull;

public class PlaceholderIntegration extends PlaceholderExpansion {

    private final TrimCooldownManager cooldownManager;

    public PlaceholderIntegration(PowerTrimss plugin) {
        this.cooldownManager = plugin.getCooldownManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "powertrims";
    }

    @Override
    public @NotNull String getAuthor() {
        return "div";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";

        TrimPattern trim = ArmourChecking.getEquippedTrim(player);

        switch (identifier) {
            case "equipped_trim":
                return (trim != null) ? cooldownManager.getTrimDisplayName(trim) : "None";

            case "cooldown_seconds":
                if (trim == null) return "0";
                long cooldown = cooldownManager.getRemainingCooldown(player, trim) / 1000;
                return String.valueOf(cooldown);

            case "cooldown_formatted":
                if (trim == null) return "Not Available";
                long remaining = cooldownManager.getRemainingCooldown(player, trim) / 1000;
                return (remaining > 0) ? remaining + "s" : "Ready";

            case "status":
                if (trim == null) return ChatColor.GRAY + "No Trim Equipped";
                String trimName = cooldownManager.getTrimDisplayName(trim);
                long cooldownTime = cooldownManager.getRemainingCooldown(player, trim) / 1000;
                String status = (cooldownTime > 0)
                        ? ChatColor.RED + "⏳ " + cooldownTime + "s"
                        : ChatColor.GREEN + "✅ Ready";
                return ChatColor.GOLD + trimName + ": " + status;

            default:
                return null;
        }
    }

    public void cleanup() {
        this.unregister();
    }
}
