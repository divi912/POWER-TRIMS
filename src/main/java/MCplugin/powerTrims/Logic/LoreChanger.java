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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;


public final class LoreChanger implements Listener {
    private static final Material SILENCE_TRIM_TEMPLATE = Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material VEX_TRIM_TEMPLATE = Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material WILD_TRIM_TEMPLATE = Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material TIDE_TRIM_TEMPLATE = Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material EYE_TRIM_TEMPLATE = Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material WARD_TRIM_TEMPLATE = Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material SPIRE_TRIM_TEMPLATE = Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material SENTRY_TRIM_TEMPLATE = Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material COAST_TRIM_TEMPLATE = Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material WAYFINDER_TRIM_TEMPLATE = Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material HOST_TRIM_TEMPLATE = Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material RAISER_TRIM_TEMPLATE = Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material DUNE_TRIM_TEMPLATE = Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material SNOUT_TRIM_TEMPLATE = Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material BOLT_TRIM_TEMPLATE = Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE;


    // New Rib Trim Template constant (assumes the material exists in your context)
    private static final Material RIB_TRIM_TEMPLATE = Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE;
    // New Flow Trim Template constant (assumes the material exists in your context)
    private static final Material FLOW_TRIM_TEMPLATE = Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE;

    private synchronized void updateTrimLore(ItemStack item) {
        if (item == null || !isTrimTemplate(item.getType())) return;

        ItemStack itemCopy = item.clone();
        ItemMeta meta = itemCopy.getItemMeta();
        if (meta == null) return;

        List<String> lore = new ArrayList<>();

        if (item.getType() == SILENCE_TRIM_TEMPLATE) {
            // Silence Trim Lore
            meta.setDisplayName(ChatColor.DARK_RED + "༄ Silence Trim ༄");

            lore.add("");

            // Primary Ability
            lore.add(ChatColor.RED + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Warden's Roar" + ChatColor.RED + " ❖");
            lore.add(ChatColor.GRAY + "✦ Releases a terrifying " + ChatColor.YELLOW + "Warden's Roar" + ChatColor.DARK_GRAY + " that disables Ender Pearls in a " + ChatColor.LIGHT_PURPLE + "15-block radius");
            lore.add(ChatColor.GRAY + "✦ Blinds and slows all enemies in range for " + ChatColor.LIGHT_PURPLE + "10 sec");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "1.5 minutes");
            lore.add("");

            // Passive Ability
            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Strength" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Strength II" + ChatColor.DARK_GRAY + " while wearing Full Silenced Trim Armor");
            lore.add("");

            // Disrupt Ability
            lore.add(ChatColor.DARK_PURPLE + "❖ " + ChatColor.GOLD + "Disrupt Ability: " + ChatColor.BOLD + "Warden's Echo" + ChatColor.DARK_PURPLE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Triggers automatically when you fall below " + ChatColor.YELLOW + "5 Hearts");
            lore.add(ChatColor.GRAY + "✦ Releases an " + ChatColor.YELLOW + "Echo Pulse" + ChatColor.DARK_GRAY + " that knocks back enemies");
            lore.add(ChatColor.GRAY + "✦ Grants temporary " + ChatColor.YELLOW + "Resistance II" + ChatColor.GRAY + " and " + ChatColor.YELLOW + "Regeneration II");
            lore.add(ChatColor.GRAY + "✦ Inflicts " + ChatColor.YELLOW + "Weakness" + ChatColor.DARK_GRAY + " on enemies for 5 sec");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 min");
            lore.add("");

        } else if (item.getType() == VEX_TRIM_TEMPLATE) {
            // Spectral Vex Trim Lore
            meta.setDisplayName(ChatColor.AQUA + "༄ Spectral Vex Trim ༄");

            lore.add("");

            // Primary Ability
            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Intimidating Aura" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ For few Moments" + ChatColor.YELLOW + " Debuff nearby players");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

            // Passive Ability
            lore.add(ChatColor.LIGHT_PURPLE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Speed II" + ChatColor.LIGHT_PURPLE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Speed II" + ChatColor.DARK_GRAY + " while wearing Full Vex Trimmed Armor");
            lore.add("");

            // Disrupt Ability
            lore.add(ChatColor.DARK_PURPLE + "❖ " + ChatColor.GOLD + "Disrupt Ability: " + ChatColor.BOLD + "True Invisibility" + ChatColor.DARK_PURPLE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "True Invisibility" + ChatColor.GRAY + " for 10 seconds");
            lore.add(ChatColor.GRAY + "✦ Makes you invisible even your armor and item in hand");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

        }else if (item.getType() == SNOUT_TRIM_TEMPLATE) {
            // Snout Trim Lore
            meta.setDisplayName(ChatColor.DARK_GRAY + "🪦 Snout Trim 🪦");

            lore.add("");
            // Passive Ability
            lore.add(ChatColor.DARK_GRAY + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Unyielding Fury" + ChatColor.DARK_GRAY + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants permanent " + ChatColor.YELLOW + "Strength I" + ChatColor.DARK_GRAY + " while wearing Full Snout Trim Armor");
            lore.add("");

            // Primary Ability
            lore.add(ChatColor.DARK_RED + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Spectral Summons" + ChatColor.DARK_RED + " ❖");
            lore.add(ChatColor.GRAY + "✦ Summons a few " + ChatColor.YELLOW + "Wither Skeletons" + ChatColor.DARK_GRAY + " to fight by your side.");
            lore.add(ChatColor.GRAY + "✦ These warriors will:");
            lore.add(ChatColor.GRAY + "  - " + ChatColor.YELLOW + "Fight entities who attack you");
            lore.add(ChatColor.GRAY + "  - " + ChatColor.YELLOW + "Fight entities that You attack");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");
        }
        else if (item.getType() == COAST_TRIM_TEMPLATE) {
            // Coast Trim Lore
            meta.setDisplayName(ChatColor.AQUA + "༄ Coast Trim ༄");

            lore.add("");
            // Passive Ability
            lore.add(ChatColor.AQUA + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Dolphin's Grace" + ChatColor.AQUA + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants permanent " + ChatColor.YELLOW + "Dolphin's Grace" + ChatColor.DARK_GRAY + " while wearing Full Coast Trim Armor");
            lore.add("");

            // Primary Ability
            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Tidal Pull" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Instantly pulls all nearby " + ChatColor.YELLOW + "players and mobs" + ChatColor.DARK_GRAY + " toward you within a " + ChatColor.LIGHT_PURPLE + "10-block radius");
            lore.add(ChatColor.GRAY + "✦ After pulling them in, releases a " + ChatColor.YELLOW + "powerful burst of water" + ChatColor.DARK_GRAY + " that pulls entities towards you");
            lore.add(ChatColor.GRAY + "✦ Inflicts " + ChatColor.YELLOW + "Weakness II" + ChatColor.DARK_GRAY + " and " + ChatColor.YELLOW + "Slowness II" + ChatColor.DARK_GRAY + " for 10 sec");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "1 minute");
            lore.add("");

        } else if (item.getType() == WILD_TRIM_TEMPLATE) {
            // Wild Trim Lore
            meta.setDisplayName(ChatColor.DARK_GREEN + "༄ Wild Trim ༄");

            lore.add("");
            lore.add("");
            lore.add(ChatColor.GREEN + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Vine Grapple" + ChatColor.GREEN + " ❖");

            lore.add(ChatColor.GRAY + "✦ Launch a " + ChatColor.YELLOW + "vine" + ChatColor.DARK_GREEN + " up to 60 blocks");
            lore.add(ChatColor.GRAY + "✦ Can grapple to both " + ChatColor.YELLOW + "blocks" + ChatColor.DARK_GREEN + " and " + ChatColor.YELLOW + "enemies");
            lore.add(ChatColor.GRAY + "✦ Grappling an enemy " + ChatColor.YELLOW + "poisons" + ChatColor.DARK_GREEN + " them for 5 sec");
            lore.add(ChatColor.GRAY + "✦ Great for mobility and initiating fights");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "30 seconds");
            lore.add("");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Regen 1" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Regeneration I" + ChatColor.DARK_GREEN + " while wearing Full Wild Trim Armor");
            lore.add("");

            lore.add(ChatColor.DARK_PURPLE + "❖ " + ChatColor.GOLD + "Disrupt Ability: " + ChatColor.BOLD + "Root Trap" + ChatColor.DARK_PURPLE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Triggers automatically when you get " + ChatColor.YELLOW + "Low");
            lore.add(ChatColor.GRAY + "✦ Entangles nearby enemies with " + ChatColor.YELLOW + "roots," + ChatColor.DARK_GREEN + " preventing movement");
            lore.add(ChatColor.GRAY + "✦ Roots last for " + ChatColor.YELLOW + "4 seconds" + ChatColor.DARK_GREEN + " before breaking");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "20 seconds");
            lore.add("");

        } else if (item.getType() == TIDE_TRIM_TEMPLATE) {
            // Tide Trim Lore
            meta.setDisplayName(ChatColor.AQUA + "༄ Tide Trim ༄");

            lore.add("");
            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Tidal Wave" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Summons a massive " + ChatColor.YELLOW + "wall of water");
            lore.add(ChatColor.GRAY + "✦ Wall moves forward up to " + ChatColor.YELLOW + "20 blocks");
            lore.add(ChatColor.GRAY + "✦ Pushes back and slows enemies in its path");
            lore.add(ChatColor.GRAY + "✦ Wall height: " + ChatColor.YELLOW + "6 blocks");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Water Affinity" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Dolphin's Grace 3" + ChatColor.DARK_AQUA + " while wearing Full Tide Trim Armor");
            lore.add("");

        } else if (item.getType() == DUNE_TRIM_TEMPLATE) {
            // Dune Trim Lore
            meta.setDisplayName(ChatColor.GOLD + "༄ Dune Trim ༄");

            lore.add("");
            lore.add(ChatColor.AQUA + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Sandstorm" + ChatColor.AQUA + " ❖");
            lore.add(ChatColor.GRAY + "✦ Unleash a powerful " + ChatColor.YELLOW + "sandstorm");
            lore.add(ChatColor.GRAY + "✦ Blinds and slows all nearby enemies");
            lore.add(ChatColor.GRAY + "✦ Knocks back enemies with " + ChatColor.RED + "forceful winds");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Strength" + ChatColor.GRAY + " and " + ChatColor.YELLOW + "Speed" + ChatColor.GRAY + " for 5 seconds");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "1 minutes");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Desert Resilience" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Haste" + ChatColor.GRAY + " and " + ChatColor.YELLOW + "Fire Resistance");
            lore.add(ChatColor.GRAY + "✦ Effects remain while wearing " + ChatColor.YELLOW + "Full Dune Trim Armor");
            lore.add("");
        }
        else if (item.getType() == EYE_TRIM_TEMPLATE) {
            // Eye Trim Lore
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "༄ Eye Trim ༄");

            lore.add("");
            lore.add(ChatColor.AQUA + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "True Sight" + ChatColor.AQUA + " ❖");
            lore.add(ChatColor.GRAY + "✦ Reveals all entities within " + ChatColor.YELLOW + "Few chunks");
            lore.add(ChatColor.GRAY + "✦ Removes " + ChatColor.YELLOW + "invisibility" + ChatColor.DARK_GRAY + " from hidden enemies");
            lore.add(ChatColor.GRAY + "✦ Makes all entities " + ChatColor.YELLOW + "glow" + ChatColor.DARK_GRAY + " for easy tracking");
            lore.add(ChatColor.GRAY + "✦ Makes all entities " + ChatColor.YELLOW + "gets SLowed and Weaked" + ChatColor.DARK_GRAY + " for easy rop");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Night Vision" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Night Vision" + ChatColor.DARK_GRAY + " while wearing Full Eye Trim Armor");
            lore.add("");

        } else if (item.getType() == WARD_TRIM_TEMPLATE) {
            // Ward Trim Lore
            meta.setDisplayName(ChatColor.GOLD + "༄ Ward Trim ༄");

            lore.add("");
            lore.add(ChatColor.YELLOW + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Protective Barrier" + ChatColor.YELLOW + " ❖");
            lore.add(ChatColor.GRAY + "✦ Creates a personal protective barrier");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Absorption V" + ChatColor.DARK_GRAY + " and " + ChatColor.YELLOW + "Resistance III");
            lore.add(ChatColor.GRAY + "✦ Provides " + ChatColor.YELLOW + "Fire Resistance" + ChatColor.DARK_GRAY + " for 10 seconds");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Protection" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Resistance I" + ChatColor.DARK_GRAY + " while wearing Full Ward Trim Armor");
            lore.add("");
        }else if (item.getType() == WAYFINDER_TRIM_TEMPLATE) {
            // Sentry Trim Lore
            meta.setDisplayName(ChatColor.DARK_RED + "༄ Wayfinder Trim ༄");

            lore.add("");
            lore.add(ChatColor.YELLOW + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Marker" + ChatColor.YELLOW + " ❖");
            lore.add(ChatColor.GRAY + "✦ mark a location for teleportation");
            lore.add(ChatColor.GRAY + "✦ infinite range");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "fast sneak" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Move fast while " + ChatColor.YELLOW + " sneaking");
            lore.add("");
        }else if (item.getType() == RAISER_TRIM_TEMPLATE) {
            // Sentry Trim Lore
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "༄ Raiser Trim ༄");

            lore.add("");
            lore.add(ChatColor.YELLOW + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Shockwave" + ChatColor.YELLOW + " ❖");
            lore.add(ChatColor.GRAY + "✦ Jump up and land to create a shockwave");
            lore.add(ChatColor.GRAY + "✦ all entities are pulled towards you and have slowness and weakness applied for few seconds");
            lore.add(ChatColor.GRAY + "✦ disable ender pearl for 10 sec");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Protection" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Negate all" + ChatColor.YELLOW + "Fall damage");
            lore.add("");
        }else if (item.getType() == HOST_TRIM_TEMPLATE) {
            // Sentry Trim Lore
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "༄ Host Trim ༄");

            lore.add("");
            lore.add(ChatColor.YELLOW + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Steal" + ChatColor.YELLOW + " ❖");
            lore.add(ChatColor.GRAY + "✦ steal health and most positive effects from nearby enemies");
            lore.add(ChatColor.GRAY + "✦ all positive are amplified by a level");
            lore.add(ChatColor.GRAY + "✦ Knock backs every entity moderately");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Phantom presence" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Most mobs stop" + ChatColor.YELLOW + "attacking you");
            lore.add("");
        } else if (item.getType() == SENTRY_TRIM_TEMPLATE) {
            // Sentry Trim Lore
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "༄ Sentry Trim ༄");

            lore.add("");
            lore.add(ChatColor.YELLOW + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Sentry" + ChatColor.YELLOW + " ❖");
            lore.add(ChatColor.GRAY + "✦ fire spectral arrows to inflict large damage on nearby enemies");
            lore.add(ChatColor.GRAY + "✦ deal goods amount of damage to nearby enemies");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "2 minutes");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Protection" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Resistance I" + ChatColor.DARK_GRAY + " while wearing Full Sentry Trim Armor");
            lore.add("");
        } else if (item.getType() == SPIRE_TRIM_TEMPLATE) {
            // Spire Trim Lore
            meta.setDisplayName(ChatColor.DARK_PURPLE + "༄ Spire Trim ༄");

            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Spire Dash" + ChatColor.LIGHT_PURPLE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Dash forward " + ChatColor.YELLOW + "8 blocks" + ChatColor.DARK_GRAY + " in your looking direction");
            lore.add(ChatColor.GRAY + "✦ Enemies hit during dash are:");
            lore.add(ChatColor.GRAY + "  • Knockbacked, and take more damage");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "30 seconds");
            lore.add("");

            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Speed" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Speed II" + ChatColor.DARK_GRAY + " while wearing Full Spire Trim Armor");
            lore.add("");
        } else if (item.getType() == RIB_TRIM_TEMPLATE) {
            // Rib Trim Lore
            meta.setDisplayName(ChatColor.WHITE + "༄ Rib Trim ༄");

            lore.add("");
            lore.add(ChatColor.GRAY + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Summon" + ChatColor.GRAY + " ❖");
            lore.add(ChatColor.GRAY + "✦ Summon few skeletons to fight for you for ");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "1 minute");
            lore.add("");
            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Bone Resilience" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Resistance I" + ChatColor.DARK_GRAY + " while wearing Full Rib Trim Armor");
            lore.add("");
        } else if (item.getType() == FLOW_TRIM_TEMPLATE) {
            // Flow Trim Lore
            meta.setDisplayName(ChatColor.AQUA + "༄ Flow Trim ༄");

            lore.add("");
            // Primary Ability: Gale Dash
            lore.add(ChatColor.AQUA + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Jetpack" + ChatColor.AQUA + " ❖");
            lore.add(ChatColor.GRAY + "✦ fly rapidly, propelled by gusts of wind");
            lore.add(ChatColor.GRAY + "✦ Consumes 1 heart per second to fly");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "1 minute");
            lore.add("");
            // Passive Ability: Flowing Agility
            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Flowing Agility" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Speed II" + ChatColor.DARK_GRAY + " while wearing Full Flow Trim Armor");
            lore.add("");
        } else if (item.getType() == BOLT_TRIM_TEMPLATE) {
            // Bolt Trim Lore
            meta.setDisplayName(ChatColor.YELLOW + "༄ Bolt Trim ༄");

            lore.add("");
            // Primary Ability: Chain Lightning
            lore.add(ChatColor.YELLOW + "❖ " + ChatColor.GOLD + "Primary Ability: " + ChatColor.BOLD + "Chain Lightning" + ChatColor.YELLOW + " ❖");
            lore.add(ChatColor.GRAY + "✦ Strike a target with a bolt of lightning");
            lore.add(ChatColor.GRAY + "✦ The lightning will chain to nearby enemies");
            lore.add(ChatColor.GOLD + "⏳ Cooldown: " + ChatColor.YELLOW + "20 seconds");
            lore.add("");
            // Passive Ability: Speed
            lore.add(ChatColor.BLUE + "❖ " + ChatColor.GOLD + "Passive Ability: " + ChatColor.BOLD + "Speed" + ChatColor.BLUE + " ❖");
            lore.add(ChatColor.GRAY + "✦ Grants " + ChatColor.YELLOW + "Speed II" + ChatColor.DARK_GRAY + " while wearing Full Bolt Trim Armor");
            lore.add("");
        }

        meta.setLore(lore);
        itemCopy.setItemMeta(meta);
        item.setItemMeta(itemCopy.getItemMeta());
    }

    private boolean isTrimTemplate(Material material) {
        return material == SILENCE_TRIM_TEMPLATE ||
                material == VEX_TRIM_TEMPLATE ||
                material == WILD_TRIM_TEMPLATE ||
                material == TIDE_TRIM_TEMPLATE ||
                material == EYE_TRIM_TEMPLATE ||
                material == WARD_TRIM_TEMPLATE ||
                material == SENTRY_TRIM_TEMPLATE ||
                material == SPIRE_TRIM_TEMPLATE ||
                material == RIB_TRIM_TEMPLATE ||
                material == HOST_TRIM_TEMPLATE ||
                material == RAISER_TRIM_TEMPLATE ||
                material == WAYFINDER_TRIM_TEMPLATE ||
                material == COAST_TRIM_TEMPLATE ||
                material == DUNE_TRIM_TEMPLATE ||
                material == SNOUT_TRIM_TEMPLATE ||
                material == FLOW_TRIM_TEMPLATE ||
                material == BOLT_TRIM_TEMPLATE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) return;

        ItemStack item = event.getItem();
        if (isTrimTemplate(item.getType())) {
            updateTrimLore(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item != null && isTrimTemplate(item.getType())) {
            updateTrimLore(item);
        }
    }
}
