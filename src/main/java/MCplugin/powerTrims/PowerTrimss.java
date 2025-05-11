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
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PowerTrimss extends JavaPlugin implements Listener {
    private TrimCooldownManager cooldownManager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Stylish startup message
        getLogger().info(ChatColor.GREEN + "--------------------------------------");
        getLogger().info(ChatColor.GOLD + "   Thanks for using PowerTrims!");
        getLogger().info(ChatColor.AQUA + "   Made by " + ChatColor.BOLD + "div");
        getLogger().info(ChatColor.GREEN + "--------------------------------------");



        saveDefaultConfig();

        dataManager = new DataManager(this);
        cooldownManager = new TrimCooldownManager(this);



        // Register trim abilities
        registerTrimAbilities();

        getServer().getPluginManager().registerEvents(new LoreChanger(), this);
    }

    private void registerTrimAbilities() {
        getServer().getPluginManager().registerEvents(new SilenceTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new WildTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new VexTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new TideTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new EyeTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new RibTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new FlowTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new CoastTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new DuneTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new SentryTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new WayfinderTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new RaiserTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new WardTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new SpireTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new HostTrim(this, cooldownManager), this);
        getServer().getPluginManager().registerEvents(new SnoutTrim(this, cooldownManager), this);
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(ChatColor.DARK_AQUA + "§l★ §eThank you for using " + ChatColor.GOLD + "PowerTrims! " + ChatColor.DARK_AQUA + "§l★");
        event.getPlayer().sendMessage(ChatColor.YELLOW + "§lMade by " + ChatColor.RED + "div" + ChatColor.YELLOW + " §l♥");
    }


    @Override
    public void onDisable() {

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
}
