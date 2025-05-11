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
import org.bukkit.plugin.java.JavaPlugin;

public final class PowerTrimss extends JavaPlugin {
    private TrimCooldownManager cooldownManager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
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

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveData();
        }
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
