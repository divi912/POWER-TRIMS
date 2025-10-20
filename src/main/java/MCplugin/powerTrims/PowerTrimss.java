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
import MCplugin.powerTrims.UltimateUpgrader.RitualManager;
import MCplugin.powerTrims.UltimateUpgrader.UltimateUpgraderManager;
import MCplugin.powerTrims.commands.PowerTrimsCommand;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.PlaceholderIntegration;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import MCplugin.powerTrims.integrations.geyser.DoubleSneakManager;
import MCplugin.powerTrims.ultimates.silenceult.SilenceUlt;
import com.jeff_media.armorequipevent.ArmorEquipEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Objects;


public final class PowerTrimss extends JavaPlugin implements Listener {
    private TrimCooldownManager cooldownManager;
    private DataManager dataManager;
    private PersistentTrustManager trustManager;
    private ConfigManager configManager;
    private TrimEffectManager trimEffectManager;
    private AbilityManager abilityManager;
    private RitualManager ritualManager;
    private UltimateUpgraderManager ultimateUpgraderManager;
    private NamespacedKey upgradeKey;
    private SilenceUlt silenceUlt;

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            getLogger().info("WorldGuard detected. Registering custom PowerTrims flags...");
            WorldGuardIntegration.registerFlags();
        }
    }


    @Override
    public void onEnable() {
        saveDefaultConfig();
        setConfigDefaults();
        getConfig().options().copyDefaults(true);
        saveConfig();

        configManager = new ConfigManager(this);
        trustManager = new PersistentTrustManager(this);
        dataManager = new DataManager(this);
        cooldownManager = new TrimCooldownManager(this);
        abilityManager = new AbilityManager();
        new DoubleSneakManager(this, abilityManager);

        this.upgradeKey = new NamespacedKey(this, "silence_ultimate_upgraded");
        this.ritualManager = new RitualManager(this, upgradeKey);
        this.ultimateUpgraderManager = new UltimateUpgraderManager(this, ritualManager, upgradeKey, configManager);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderIntegration(this).register();
        }


        registerTrimAbilities();

        ArmorEquipEvent.registerListener(this);
        this.trimEffectManager = new TrimEffectManager(this, configManager);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new LoreChanger(), this);

        Objects.requireNonNull(getCommand("powertrims")).setExecutor(new PowerTrimsCommand(this, configManager, cooldownManager, trustManager));

        getLogger().info(ChatColor.GREEN + "--------------------------------------");
        getLogger().info(ChatColor.GOLD + "   Thanks for using PowerTrims!");
        getLogger().info(ChatColor.AQUA + "   Made by " + ChatColor.BOLD + "div");
        getLogger().info(ChatColor.GREEN + "--------------------------------------");
    }

    public void reloadPlugin() {
        HandlerList.unregisterAll((Plugin) this);
        configManager.reloadConfig();
        registerTrimAbilities();
        ArmorEquipEvent.registerListener(this);
        this.trimEffectManager = new TrimEffectManager(this, configManager);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new LoreChanger(), this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            trimEffectManager.applyTrimEffects(player);
        }
    }


    private void registerTrimAbilities() {
        // Create and register the single instance of SilenceUlt
        this.silenceUlt = new SilenceUlt(this, upgradeKey);
        getServer().getPluginManager().registerEvents(this.silenceUlt, this);

        // Register other trims
        getServer().getPluginManager().registerEvents(new SilenceTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new WildTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new VexTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new TideTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new EyeTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new RibTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new FlowTrim(this, cooldownManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new CoastTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new DuneTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new SentryTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new WayfinderTrim(this, cooldownManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new RaiserTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new WardTrim(this, cooldownManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new SpireTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new HostTrim(this, cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new SnoutTrim(this,cooldownManager, trustManager, configManager, abilityManager), this);
        getServer().getPluginManager().registerEvents(new BoltTrim(this, cooldownManager, trustManager, configManager), this);
    }

    private void setConfigDefaults() {
        FileConfiguration config = getConfig();
        config.addDefault("web-address", "0.0.0.0");
        config.addDefault("web-port", 8080);
        config.addDefault("upgrades.max_level", 5);

        // Passive Effects
        config.addDefault("passive_effects.silence", Arrays.asList("STRENGTH:1"));
        config.addDefault("passive_effects.vex", Arrays.asList("SPEED:1"));
        config.addDefault("passive_effects.snout", Arrays.asList("STRENGTH:0"));
        config.addDefault("passive_effects.coast", Arrays.asList("DOLPHINS_GRACE:0"));
        config.addDefault("passive_effects.wild", Arrays.asList("REGENERATION:0"));
        config.addDefault("passive_effects.tide", Arrays.asList("DOLPHINS_GRACE:2"));
        config.addDefault("passive_effects.dune", Arrays.asList("HASTE:0", "FIRE_RESISTANCE:0"));
        config.addDefault("passive_effects.eye", Arrays.asList("NIGHT_VISION:0"));
        config.addDefault("passive_effects.ward", Arrays.asList("RESISTANCE:0"));
        config.addDefault("passive_effects.sentry", Arrays.asList("RESISTANCE:0"));
        config.addDefault("passive_effects.spire", Arrays.asList("SPEED:1"));
        config.addDefault("passive_effects.rib", Arrays.asList("RESISTANCE:0"));
        config.addDefault("passive_effects.bolt", Arrays.asList("SPEED:2"));
        config.addDefault("passive_effects.flow", Arrays.asList("SPEED:1"));

        // Silence Trim
        config.addDefault("silence.passive.cooldown", 120000L);
        config.addDefault("silence.primary.radius", 15.0);
        config.addDefault("silence.primary.potion_duration_ticks", 400);
        config.addDefault("silence.primary.pearl_cooldown_ticks", 200);
        config.addDefault("silence.passive.echo_radius", 6.0);
        config.addDefault("silence.passive.effect_duration_ticks", 300);
        config.addDefault("silence.primary.max_affected_entities", 30);
        config.addDefault("silence.primary.cooldown", 90000L);

        // Wild Trim
        config.addDefault("wild.passive.trigger_health", 8);
        config.addDefault("wild.passive.cooldown_seconds", 20);
        config.addDefault("wild.primary.cooldown", 20000L);
        config.addDefault("wild.primary.grapple_range", 60.0);
        config.addDefault("wild.primary.poison_duration_ticks", 200);
        config.addDefault("wild.primary.grapple_speed", 1.8);
        config.addDefault("wild.passive.root_trap_radius_xz", 5);
        config.addDefault("wild.passive.root_trap_radius_y", 3);
        config.addDefault("wild.passive.root_trap_duration_ticks", 200);

        // Vex Trim
        config.addDefault("vex.primary.cooldown", 120000L);
        config.addDefault("vex.primary.radius", 30.0);
        config.addDefault("vex.primary.damage", 8.0);
        config.addDefault("vex.primary.debuff_duration_ticks", 400);
        config.addDefault("vex.primary.blindness_duration_ticks", 100);
        config.addDefault("vex.passive.cooldown", 120000L);
        config.addDefault("vex.passive.hide_duration_ticks", 200L);
        config.addDefault("vex.passive.health_threshold", 8.0);

        // Tide Trim
        config.addDefault("tide.primary.cooldown", 120000L);
        config.addDefault("tide.primary.wave_width", 3.0);
        config.addDefault("tide.primary.effect_duration_ticks", 300);
        config.addDefault("tide.primary.knockback_strength", 1.8);
        config.addDefault("tide.primary.wall_height", 6);
        config.addDefault("tide.primary.move_delay_ticks", 2);
        config.addDefault("tide.primary.max_moves", 20);

        // Eye Trim
        config.addDefault("eye.primary.true_sight_radius", 80.0);
        config.addDefault("eye.primary.true_sight_duration_ticks", 600);
        config.addDefault("trim.eye.primary.cooldown", 120000L);
        config.addDefault("eye.primary.task_interval_ticks", 20L);
        config.addDefault("eye.primary.true_sight_vertical_radius", 50.0);

        // Rib Trim
        config.addDefault("rib.primary.cooldown", 60000L);
        config.addDefault("rib.primary.minion_lifespan_ticks", 1200L);

        // Flow Trim
        config.addDefault("flow.primary.heart_cost_interval", 20);
        config.addDefault("flow.primary.heart_cost_amount", 2.0);
        config.addDefault("flow.primary.cooldown", 60000L);
        config.addDefault("flow.primary.duration", 400);

        // Coast Trim
        config.addDefault("coast.primary.water-burst-radius", 30);
        config.addDefault("coast.primary.water-burst-damage", 10);
        config.addDefault("coast.primary.water-burst-cooldown", 60000L);
        config.addDefault("coast.primary.pull-duration-ticks", 60);
        config.addDefault("coast.primary.debuff-duration-ticks", 80);
        config.addDefault("coast.primary.buff-duration-ticks", 100);
        config.addDefault("coast.primary.weakness-amplifier", 1);
        config.addDefault("coast.primary.slowness-amplifier", 1);
        config.addDefault("coast.primary.speed-amplifier", 1);
        config.addDefault("coast.primary.resistance-amplifier", 0);

        // Dune Trim
        config.addDefault("dune.primary.sandstorm_radius", 12);
        config.addDefault("dune.primary.sandstorm_damage", 10);
        config.addDefault("dune.primary.cooldown", 60000L);

        // Sentry Trim
        config.addDefault("sentry.primary.arrow_count", 3);
        config.addDefault("sentry.primary.spread", 0.15);
        config.addDefault("sentry.primary.cooldown", 90000L);
        config.addDefault("sentry.primary.true_damage", 0.5);

        // Wayfinder Trim
        config.addDefault("wayfinder.primary.cooldown", 120000L);
        config.addDefault("wayfinder.primary.enabled", true);

        // Raiser Trim
        config.addDefault("raiser.primary.cooldown", 120000L);
        config.addDefault("raiser.primary.entity_pull_radius", 15.0);
        config.addDefault("raiser.primary.player_upward_boost", 1.5);
        config.addDefault("raiser.primary.pearl_cooldown_ticks", 200);

        // Ward Trim
        config.addDefault("ward.primary.barrier_duration", 200);
        config.addDefault("ward.primary.absorption_level", 4);
        config.addDefault("ward.primary.resistance_boost_level", 2);
        config.addDefault("ward.primary.cooldown", 120000L);

        // Spire Trim
        config.addDefault("spire.primary.dash_distance", 8);
        config.addDefault("spire.primary.dash_speed", 2.0);
        config.addDefault("spire.primary.knockback_strength", 1.5);
        config.addDefault("spire.primary.slow_duration", 60);
        config.addDefault("spire.primary.vulnerable_duration", 100);
        config.addDefault("spire.primary.damage_amplification", 0.6);
        config.addDefault("spire.primary.cooldown", 30000L);

        // Host Trim
        config.addDefault("host.primary.cooldown", 120000L);
        config.addDefault("host.primary.effect_steal_radius", 10.0);
        config.addDefault("host.primary.health_steal_amount", 4.0);
        config.addDefault("host.primary.particle_density", 4.0);

        // Snout Trim
        config.addDefault("snout.primary.cooldown", 120000L);
        config.addDefault("snout.primary.minion_lifespan_ticks", 1800L);

        // Bolt Trim
        config.addDefault("bolt.primary.cooldown", 20000L);
        config.addDefault("bolt.primary.chain_range", 10);
        config.addDefault("bolt.primary.max_chains", 3);
        config.addDefault("bolt.primary.initial_damage", 6.0);
        config.addDefault("bolt.primary.subsequent_damage", 4.0);
        config.addDefault("bolt.primary.target_range", 20);
        config.addDefault("bolt.primary.weakness_duration", 100);
        config.addDefault("bolt.primary.weakness_amplifier", 0);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(ChatColor.DARK_AQUA + "§l★ §eThank you for using " + ChatColor.GOLD + "PowerTrims! " + ChatColor.DARK_AQUA + "§l★");
        event.getPlayer().sendMessage(ChatColor.YELLOW + "§lMade by " + ChatColor.RED + "div" + ChatColor.YELLOW + " §l♥");
    }


    @Override
    public void onDisable() {

        if (cooldownManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                cooldownManager.removeScoreboard(player);
            }
        }

        if (trustManager != null) {
            trustManager.saveTrusts();
        }

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

    public SilenceUlt getSilenceUlt() {
        return silenceUlt;
    }

    public NamespacedKey getUpgradeKey() {
        return upgradeKey;
    }
}
