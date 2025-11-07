package MCplugin.powerTrims.Logic;

import MCplugin.powerTrims.PowerTrimss;
import MCplugin.powerTrims.ultimates.silenceult.SilenceUlt;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TrimCooldownManager {
    private final JavaPlugin plugin;
    private final DataManager dataManager;
    private DisplayMode displayMode;
    private boolean placeholderApiEnabled;

    private final PowerTrimss powerTrimsPlugin;

    public enum DisplayMode { ACTION_BAR, NONE }

    public TrimCooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.powerTrimsPlugin = (PowerTrimss) plugin;
        this.dataManager = powerTrimsPlugin.getDataManager();
        this.placeholderApiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        loadConfig();

        new BukkitRunnable() {
            @Override
            public void run() {
                updateDisplays();
            }
        }.runTaskTimer(plugin, 0L, 20L); // Update every second
    }

    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        String mode = config.getString("cooldown-display", "ACTION_BAR").toUpperCase();
        try {
            // Only allow ACTION_BAR or NONE
            if (mode.equals("SCOREBOARD")) {
                this.displayMode = DisplayMode.ACTION_BAR; // Default to action bar if scoreboard was chosen
                plugin.getLogger().warning("Scoreboard display mode is no longer supported. Using ACTION_BAR instead.");
            } else {
                this.displayMode = DisplayMode.valueOf(mode);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid cooldown-display mode in config.yml: " + mode + ". Using ACTION_BAR.");
            this.displayMode = DisplayMode.ACTION_BAR;
        }
    }

    private void updateDisplays() {
        if (displayMode == DisplayMode.NONE) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (displayMode == DisplayMode.ACTION_BAR) {
                showActionBar(player);
            }
        }
    }

    private void showActionBar(Player player) {
        TrimPattern trim = ArmourChecking.getEquippedTrim(player);
        String finalMessage = "";

        String trimCooldownString = "";
        if (trim != null) {
            long cooldown = getRemainingCooldown(player, trim) / 1000;
            String cooldownText = (cooldown > 0) ? ChatColor.RED + "⏳ " + cooldown + "s" : ChatColor.GREEN + "✅ Ready!";
            trimCooldownString = ChatColor.GRAY + "Trim: " + ChatColor.GOLD + getTrimDisplayName(trim) + " - " + cooldownText;
        }

        String ultimateString = "";
        if (trim == TrimPattern.SILENCE) {
            SilenceUlt silenceUlt = powerTrimsPlugin.getSilenceUlt();
            if (silenceUlt != null) {
                ultimateString = silenceUlt.getUltimateActionbarString(player);
            }
        }

        finalMessage = trimCooldownString;
        if (!ultimateString.isEmpty()) {
            if (!finalMessage.isEmpty()) {
                finalMessage += ChatColor.DARK_GRAY + " | ";
            }
            finalMessage += ultimateString;
        }

        if (!finalMessage.isEmpty()) {
            player.sendActionBar(Component.text(finalMessage));
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

    public void resetAllCooldowns(Player player) {
        dataManager.setCooldown(player,  TrimPattern.SILENCE, 0);
        dataManager.setCooldown(player, TrimPattern.SPIRE, 0);
        dataManager.setCooldown(player, TrimPattern.WAYFINDER, 0);
        dataManager.setCooldown(player, TrimPattern.RAISER, 0);
        dataManager.setCooldown(player, TrimPattern.DUNE, 0);
        dataManager.setCooldown(player, TrimPattern.HOST, 0);
        dataManager.setCooldown(player, TrimPattern.COAST, 0);
        dataManager.setCooldown(player, TrimPattern.FLOW, 0);
        dataManager.setCooldown(player, TrimPattern.RIB, 0);
        dataManager.setCooldown(player, TrimPattern.SENTRY, 0);
        dataManager.setCooldown(player, TrimPattern.WILD, 0);
        dataManager.setCooldown(player, TrimPattern.WARD, 0);
        dataManager.setCooldown(player, TrimPattern.EYE, 0);
        dataManager.setCooldown(player, TrimPattern.TIDE, 0);
        dataManager.setCooldown(player, TrimPattern.SNOUT, 0);
        dataManager.setCooldown(player, TrimPattern.VEX, 0);
    }

    public String getTrimDisplayName(TrimPattern pattern) {
        if (pattern == null) return "";
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
