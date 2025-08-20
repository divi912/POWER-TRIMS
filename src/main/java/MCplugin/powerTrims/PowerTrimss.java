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

package MCplugin.powerTrims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.Trims.*;
import MCplugin.powerTrims.commands.PowerTrimsCommand;
import MCplugin.powerTrims.commands.ResetCooldownsCommand;
import MCplugin.powerTrims.integrations.PlaceholderIntegration;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import com.jeff_media.armorequipevent.ArmorEquipEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;


public final class PowerTrimss extends JavaPlugin implements Listener {
    private TrimCooldownManager cooldownManager;
    private DataManager dataManager;
    private PersistentTrustManager trustManager;
    private ConfigManager configManager;


    @Override
    public void onLoad() {
        // Register WorldGuard flags as early as possible.
        // This is safe because the flag registry is available during the onLoad phase,
        // before other plugins are fully enabled.
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            getLogger().info("WorldGuard detected. Registering custom PowerTrims flags...");
            WorldGuardIntegration.registerFlags();
        }
    }


    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize managers
        configManager = new ConfigManager(this);
        trustManager = new PersistentTrustManager(this);
        dataManager = new DataManager(this);
        cooldownManager = new TrimCooldownManager(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderIntegration(this).register();
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            WorldGuardIntegration.registerFlags();
        }

        // Register trim abilities first to use below
        registerTrimAbilities();

        // Register core events
        ArmorEquipEvent.registerListener(this);
        new TrimEffectManager(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new LoreChanger(), this);

        // Register commands
        getCommand("trust").setExecutor(this);
        getCommand("untrust").setExecutor(this);
        getCommand("resettrimcooldowns").setExecutor(new ResetCooldownsCommand(this));
        getCommand("powertrims").setExecutor(new PowerTrimsCommand(configManager));

        // Stylish startup message
        getLogger().info(ChatColor.GREEN + "--------------------------------------");
        getLogger().info(ChatColor.GOLD + "   Thanks for using PowerTrims!");
        getLogger().info(ChatColor.AQUA + "   Made by " + ChatColor.BOLD + "div");
        getLogger().info(ChatColor.GREEN + "--------------------------------------");


    }


    private void registerTrimAbilities() {
        getServer().getPluginManager().registerEvents(new SilenceTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new WildTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new VexTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new TideTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new EyeTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new RibTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new FlowTrim(this, cooldownManager, configManager), this);
        getServer().getPluginManager().registerEvents(new CoastTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new DuneTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new SentryTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new WayfinderTrim(this, cooldownManager, configManager), this);
        getServer().getPluginManager().registerEvents(new RaiserTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new WardTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new SpireTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new HostTrim(this, cooldownManager, trustManager, configManager), this);
        getServer().getPluginManager().registerEvents(new SnoutTrim(this,cooldownManager, trustManager, configManager), this);
    }

    //trust command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (command.getName().equalsIgnoreCase("trust")) {
                if (args.length == 1) {
                    String targetName = args[0];
                    Player targetPlayer = Bukkit.getPlayer(targetName);
                    if (targetPlayer != null) {
                        trustManager.trustPlayer(player.getUniqueId(), targetPlayer.getUniqueId(), sender);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                    }
                } else {
                    return false; // Invalid number of arguments
                }
                return true;

            } else if (command.getName().equalsIgnoreCase("untrust")) {
                if (args.length == 1) {
                    String targetName = args[0];
                    Player targetPlayer = Bukkit.getPlayer(targetName);
                    if (targetPlayer != null) {
                        trustManager.untrustPlayer(player.getUniqueId(), targetPlayer.getUniqueId(), sender);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                    }
                } else {
                    return false; // Invalid number of arguments
                }
                return true;

            } else if (command.getName().equalsIgnoreCase("trustlist")) {
                trustManager.showTrustList(player.getUniqueId(), sender);
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
        }
        return true;
    }




    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(ChatColor.DARK_AQUA + "§l★ §eThank you for using " + ChatColor.GOLD + "PowerTrims! " + ChatColor.DARK_AQUA + "§l★");
        event.getPlayer().sendMessage(ChatColor.YELLOW + "§lMade by " + ChatColor.RED + "div" + ChatColor.YELLOW + " §l♥");
    }


    @Override
    public void onDisable() {
        // Clear scoreboards for all online players to prevent them from getting stuck.
        if (cooldownManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                cooldownManager.removeScoreboard(player);
            }
        }

        if (trustManager != null) {
            trustManager.saveTrusts();
        }

        // Stylish shutdown message
        getLogger().info(ChatColor.RED + "--------------------------------------");
        getLogger().info(ChatColor.GOLD + "   PowerTrims plugin has been disabled.");
        getLogger().info(ChatColor.RED + "--------------------------------------");



        if (dataManager != null) {
            dataManager.saveData();
        }
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public TrimCooldownManager getCooldownManager() {
        return cooldownManager;
    }
}
