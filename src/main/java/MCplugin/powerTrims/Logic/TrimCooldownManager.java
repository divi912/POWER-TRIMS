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

import me.clip.placeholderapi.PlaceholderAPI;
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
    private DisplayMode displayMode;
    private boolean placeholderApiEnabled;

    public enum DisplayMode { SCOREBOARD, ACTION_BAR, NONE }

    public TrimCooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.dataManager = ((MCplugin.powerTrims.PowerTrimss) plugin).getDataManager();
        this.placeholderApiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        loadConfig();

        new BukkitRunnable() {
            @Override
            public void run() {
                updateDisplays();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        String mode = config.getString("cooldown-display", "ACTION_BAR").toUpperCase();
        try {
            this.displayMode = DisplayMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid cooldown-display mode in config.yml: " + mode + ". Using ACTION_BAR.");
            this.displayMode = DisplayMode.ACTION_BAR;
        }
    }

    private void updateDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (displayMode == DisplayMode.SCOREBOARD) {
                showScoreboard(player);
            } else if (displayMode == DisplayMode.ACTION_BAR) {
                showActionBar(player);
            } else {
                // If display is NONE or something else, ensure our objective is removed.
                Scoreboard board = player.getScoreboard();
                if (board.getObjective("TrimCooldown") != null) {
                    board.getObjective("TrimCooldown").unregister();
                }
            }
        }
    }

    private void showActionBar(Player player) {
        TrimPattern trim = ArmourChecking.getEquippedTrim(player);
        if (trim == null) {
             // Potentially hide action bar if no trim is equipped. For now, do nothing.
            return;
        }

        long cooldown = getRemainingCooldown(player, trim) / 1000;
        Component msg = Component.text("⏳ " + getTrimDisplayName(trim) + " – ")
                .append(Component.text((cooldown > 0) ? "⏳ " + cooldown + "s" : "✅ Ready!")
                        .color(cooldown > 0 ? NamedTextColor.RED : NamedTextColor.GREEN));

        player.sendActionBar(msg);
    }

    private void showScoreboard(Player player) {
        Scoreboard board = player.getScoreboard();

        // To prevent conflicts, we should use a new scoreboard if the player is using the main one.
        if (board == scoreboardManager.getMainScoreboard()) {
            board = scoreboardManager.getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective obj = board.getObjective("TrimCooldown");
        if (obj != null) {
            obj.unregister();
        }
        obj = board.registerNewObjective("TrimCooldown", "dummy", "Title"); // Title is set below
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        if (placeholderApiEnabled) {
            // Use PAPI to parse placeholders, ensuring maximum compatibility
            obj.displayName(Component.text(PlaceholderAPI.setPlaceholders(player, "§6❖ §lTrim Status §6❖")));
            obj.getScore("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━§8").setScore(9);
            obj.getScore(PlaceholderAPI.setPlaceholders(player, "§e⚔ §lTrim: §b%powertrims_equipped_trim%")).setScore(9);
            obj.getScore("§r ").setScore(8);
            obj.getScore(PlaceholderAPI.setPlaceholders(player, "§e⚡ §lStatus: §r%powertrims_status%")).setScore(7);
            obj.getScore("§r  ").setScore(5);
            obj.getScore("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━§0").setScore(4);
            obj.getScore("§c         Created by §ldiv").setScore(3);
            obj.getScore("§8     ⚡ §6§l POWER TRIMS§8 ⚡").setScore(1);
        } else {
            // Fallback to manual string concatenation if PAPI is not available
            obj.displayName(Component.text(ChatColor.GOLD + "❖ " + ChatColor.BOLD + "Trim Status" + ChatColor.RESET + ChatColor.GOLD + " ❖"));

            TrimPattern equippedTrim = ArmourChecking.getEquippedTrim(player);

            obj.getScore(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + ChatColor.DARK_GRAY).setScore(9);

            String trimDisplay = (equippedTrim != null) ? ChatColor.AQUA + getTrimDisplayName(equippedTrim) : ChatColor.RED + "No Power";
            obj.getScore(ChatColor.YELLOW + "⚔ " + ChatColor.BOLD + "Trim: " + trimDisplay).setScore(8);

            obj.getScore(ChatColor.RESET + " ").setScore(7);

            if (equippedTrim != null) {
                long cooldownTime = getRemainingCooldown(player, equippedTrim) / 1000;
                String cooldownText = (cooldownTime > 0)
                        ? ChatColor.RED + "⏳ " + cooldownTime + "s"
                        : ChatColor.GREEN + "✓ Ready!";
                obj.getScore(ChatColor.YELLOW + "⚡ " + ChatColor.BOLD + "Status: " + ChatColor.RESET + cooldownText).setScore(6);
            } else {
                obj.getScore(ChatColor.YELLOW + "⚡ " + ChatColor.BOLD + "Status: " + ChatColor.GRAY + "None").setScore(6);
            }

            obj.getScore(ChatColor.RESET + "  ").setScore(5);
            obj.getScore(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + ChatColor.BLACK).setScore(4);
            obj.getScore(ChatColor.DARK_RED+ "         Created by " + ChatColor.RED + "div").setScore(3);
            obj.getScore(ChatColor.DARK_GRAY + "     ⚡ " + ChatColor.GOLD + ChatColor.BOLD + " POWER TRIMS" + ChatColor.DARK_GRAY + " ⚡").setScore(2);
        }
    }

    public void setCooldown(Player player, TrimPattern trim, long cooldownMillis) {
        long expiry = System.currentTimeMillis() + cooldownMillis;
        dataManager.setCooldown(player, trim, expiry);
    }

    public boolean isOnCooldown(Player player, TrimPattern trim) {
        long cooldownExpiry = dataManager.getCooldown(player, trim);
        return System.currentTimeMillis() < cooldownExpiry;
    }

    public long getRemainingCooldown(Player player, TrimPattern trim) {
        long cooldownExpiry = dataManager.getCooldown(player, trim);
        long remaining = cooldownExpiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public String getTrimDisplayName(TrimPattern pattern) {
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

    public void removeScoreboard(Player player) {
        if (player.isOnline() && player.getScoreboard() != scoreboardManager.getMainScoreboard()) {
            player.setScoreboard(scoreboardManager.getMainScoreboard());
        }
    }
}
