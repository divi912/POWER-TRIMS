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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

public class TrimCooldownManager {
    private final JavaPlugin plugin;
    private final ScoreboardManager scoreboardManager;
    private final DataManager dataManager;
    private final DisplayMode displayMode;

    public enum DisplayMode { SCOREBOARD, ACTION_BAR, NONE }

    public TrimCooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.dataManager = ((MCplugin.powerTrims.PowerTrimss) plugin).getDataManager();

        FileConfiguration config = plugin.getConfig();
        String mode = config.getString("cooldown-display", "ACTION_BAR").toUpperCase();
        DisplayMode loadedMode;
        try {
            loadedMode = DisplayMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid cooldown-display mode in config.yml: " + mode + ". Using ACTION_BAR.");
            loadedMode = DisplayMode.ACTION_BAR;
        }
        this.displayMode = loadedMode;

        new BukkitRunnable() {
            @Override
            public void run() {
                updateDisplays();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            switch (displayMode) {
                case SCOREBOARD -> showScoreboard(player);
                case ACTION_BAR -> showActionBar(player);
                case NONE -> {} // Do nothing
            }
        }
    }

    private void showActionBar(Player player) {
        TrimPattern trim = ArmourChecking.getEquippedTrim(player);
        if (trim == null) return;

        long cooldown = getRemainingCooldown(player, trim) / 1000;
        Component msg = Component.text("⏳ " + getTrimDisplayName(trim) + " – ")
                .append(Component.text((cooldown > 0) ? "⏳ " + cooldown + "s" : "✅ Ready!")
                        .color(cooldown > 0 ? NamedTextColor.RED : NamedTextColor.GREEN));

        player.sendActionBar(msg);
    }

    private void showScoreboard(Player player) {
        TrimPattern equippedTrim = ArmourChecking.getEquippedTrim(player);
        Scoreboard board = player.getScoreboard();

        if (board == null) return; // Safeguard — in practice, all players should have a scoreboard

        // Remove previous objective if exists
        Objective old = board.getObjective("TrimCooldown");
        if (old != null) old.unregister();

        Objective obj = board.registerNewObjective("TrimCooldown", "dummy", ChatColor.GOLD + "❖ " + ChatColor.BOLD + "Trim Status" + ChatColor.RESET + ChatColor.GOLD + " ❖");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + ChatColor.DARK_GRAY).setScore(9);
        obj.getScore(ChatColor.YELLOW + "⚔ " + ChatColor.BOLD + "Trim Power" + ChatColor.YELLOW + " ⚔ :").setScore(8);

        if (equippedTrim != null) {
            obj.getScore(ChatColor.AQUA + "  ✧ " + getTrimDisplayName(equippedTrim)).setScore(7);
        } else {
            obj.getScore(ChatColor.RED + "  ✧ No Power").setScore(7);
        }

        obj.getScore(ChatColor.RESET + " ").setScore(6);

        if (equippedTrim != null) {
            long cooldownTime = getRemainingCooldown(player, equippedTrim) / 1000;
            String cooldownText = (cooldownTime > 0)
                    ? ChatColor.RED + "⏳ " + cooldownTime + "s"
                    : ChatColor.GREEN + "✓ Ready!";
            obj.getScore(ChatColor.YELLOW + "⚡ " + ChatColor.BOLD + "Status: " + ChatColor.RESET + cooldownText).setScore(5);
        } else {
            obj.getScore(ChatColor.YELLOW + "⚡ " + ChatColor.BOLD + "Status: " + ChatColor.GRAY + "None").setScore(5);
        }

        obj.getScore(ChatColor.RESET + "  ").setScore(4);
        obj.getScore(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + ChatColor.BLACK).setScore(3);
        obj.getScore(ChatColor.DARK_RED+ "         Created by " + ChatColor.RED + "div").setScore(2);
        obj.getScore(ChatColor.DARK_GRAY + "     ⚡ " + ChatColor.GOLD + ChatColor.BOLD + " POWER TRIMS" + ChatColor.DARK_GRAY + " ⚡").setScore(1);
    }
    /** Sets a cooldown for a specific TrimPattern */
    public void setCooldown(Player player, TrimPattern trim, long cooldownMillis) {
        long expiry = System.currentTimeMillis() + cooldownMillis;
        dataManager.setCooldown(player, trim, expiry);
    }

    /** Checks if a specific trim is on cooldown */
    public boolean isOnCooldown(Player player, TrimPattern trim) {
        long cooldownExpiry = dataManager.getCooldown(player, trim);
        return System.currentTimeMillis() < cooldownExpiry;
    }

    /** Gets remaining cooldown time for a specific trim */
    public long getRemainingCooldown(Player player, TrimPattern trim) {
        long cooldownExpiry = dataManager.getCooldown(player, trim);
        long remaining = cooldownExpiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /** Displays a scoreboard showing cooldowns for all trims a player has */


    private String getTrimDisplayName(TrimPattern pattern) {
        if (pattern == TrimPattern.SILENCE) {
            return "Silence";
        } else if (pattern == TrimPattern.SPIRE) {
            return "Spire";
        }else if (pattern == TrimPattern.WAYFINDER) {
            return "Wayfinder";
        }else if (pattern == TrimPattern.RAISER) {
            return "Raiser";
        }else if (pattern == TrimPattern.HOST) {
            return "Host";
        }else if (pattern == TrimPattern.DUNE) {
            return "Dune";
        }else if (pattern == TrimPattern.COAST) {
            return "Coast";
        }else if (pattern == TrimPattern.FLOW) {
            return "Flow";
        }else if (pattern == TrimPattern.RIB) {
            return "Rib";
        } else if (pattern == TrimPattern.SENTRY) {
            return "Sentry";
        } else if (pattern == TrimPattern.WILD) {
            return "Wild";
        } else if (pattern == TrimPattern.WARD) {
            return "Ward";
        } else if (pattern == TrimPattern.EYE) {
            return "Eye";
        } else if (pattern == TrimPattern.TIDE) {
            return "Tide";
        } else if (pattern == TrimPattern.BOLT) {
            return "Bolt";
        }else if (pattern == TrimPattern.SNOUT) {
            return "Snout";
        }  else if (pattern == TrimPattern.VEX) {
            return "Vex";
        } else if (pattern == TrimPattern.SHAPER) {
            return "Shaper";
        } else {
            return pattern.toString().replace("_", " ");
        }
    }
}
