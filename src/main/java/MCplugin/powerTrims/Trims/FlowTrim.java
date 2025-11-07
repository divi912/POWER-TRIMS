package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FlowTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;
    private final NamespacedKey dashEndFallImmunityKey;

    private final int HEART_COST_INTERVAL;
    private final double HEART_COST_AMOUNT;
    private final long DASH_COOLDOWN;
    private final int DASH_DURATION;

    private final Map<UUID, BukkitRunnable> dashTasks = new HashMap<>();
    private final Map<UUID, Boolean> isDashing = new HashMap<>();
    private final Set<UUID> activationDebounce = new HashSet<>();

    public FlowTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;
        this.dashEndFallImmunityKey = new NamespacedKey(plugin, "flow_trim_dash_end_immune");

        HEART_COST_INTERVAL = configManager.getInt("flow.primary.heart_cost_interval");
        HEART_COST_AMOUNT = configManager.getDouble("flow.primary.heart_cost_amount");
        DASH_COOLDOWN = configManager.getLong("flow.primary.cooldown");
        DASH_DURATION = configManager.getInt("flow.primary.duration");

        abilityManager.registerPrimaryAbility(TrimPattern.FLOW, this::flowPrimary);
    }

    public void flowPrimary(Player player) {
        UUID id = player.getUniqueId();

        if (activationDebounce.contains(id)) {
            return;
        }

        activationDebounce.add(id);
        new BukkitRunnable() {
            @Override
            public void run() {
                activationDebounce.remove(id);
            }
        }.runTaskLater(plugin, 10L);

        if (!configManager.isTrimEnabled("flow")) return;
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.FLOW)) return;

        if (isDashing.getOrDefault(id, false)) {
            deactivateDash(player, "You have deactivated Dash.");
            return;
        }

        if (cooldownManager.isOnCooldown(player, TrimPattern.FLOW)) {
            Messaging.sendTrimMessage(player, "Flow", ChatColor.AQUA, "Ability on cooldown!");
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) {
            Messaging.sendError(player, "You cannot use this ability in the current region.");
            return;
        }

        player.setAllowFlight(true);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.5f);
        createGaleDashAnimation(player);

        BukkitRunnable task = new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !isDashing.getOrDefault(id, false)) {
                    this.cancel();
                    return;
                }

                if (tickCount >= DASH_DURATION) {
                    deactivateDash(player, "Dash has expired.");
                    this.cancel();
                    return;
                }

                if (tickCount % HEART_COST_INTERVAL == 0) {
                    double hp = player.getHealth();
                    if (hp > HEART_COST_AMOUNT) {
                        player.setHealth(hp - HEART_COST_AMOUNT);
                    } else {
                        deactivateDash(player, "Dash ended due to low health.");
                        this.cancel();
                        return;
                    }
                }

                Vector dir = player.getLocation().getDirection().normalize();
                player.setVelocity(dir.multiply(1.2));
                if (tickCount % 3 == 0) {
                    createGaleDashAnimation(player);
                }

                tickCount++;
            }
        };

        isDashing.put(id, true);
        dashTasks.put(id, task);
        task.runTaskTimer(plugin, 0L, 1L);
        Messaging.sendTrimMessage(player, "Flow", ChatColor.AQUA, "Activated dash! §7(uses " + (HEART_COST_AMOUNT / 2) + " ❤/sec)");
    }

    private void deactivateDash(Player player, String message) {
        UUID id = player.getUniqueId();
        if (dashTasks.containsKey(id)) {
            dashTasks.get(id).cancel();
            dashTasks.remove(id);
        }
        isDashing.put(id, false);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setAllowFlight(false);
        }
        cooldownManager.setCooldown(player, TrimPattern.FLOW, DASH_COOLDOWN);
        player.getPersistentDataContainer().set(dashEndFallImmunityKey, PersistentDataType.BYTE, (byte) 1);
        Messaging.sendTrimMessage(player, "Flow", ChatColor.AQUA, message);
    }

    private void createGaleDashAnimation(Player player) {
        Location center = player.getLocation().add(0, 1.2, 0);
        World world = player.getWorld();
        final List<Material> cloudMaterials = List.of(Material.WHITE_WOOL, Material.LIGHT_GRAY_WOOL, Material.WHITE_CONCRETE_POWDER);
        final int blocksPerPuff = 6;
        final int animationTicks = 40;

        for (int i = 0; i < blocksPerPuff; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            BlockDisplay block = world.spawn(center, BlockDisplay.class, bd -> {
                bd.setBlock(cloudMaterials.get(r.nextInt(cloudMaterials.size())).createBlockData());
                bd.setBrightness(new Display.Brightness(10, 10));

                bd.setInterpolationDelay(-1);
                bd.setInterpolationDuration(animationTicks);

                Transformation initialTransform = bd.getTransformation();
                initialTransform.getScale().set(r.nextFloat() * 0.4f + 0.2f);
                bd.setTransformation(initialTransform);
            });

            Vector randomOffset = new Vector(
                    (r.nextDouble() - 0.5) * 2.5,
                    (r.nextDouble() - 0.5) * 1.5,
                    (r.nextDouble() - 0.5) * 2.5
            );
            Location finalLocation = center.clone().add(randomOffset).subtract(player.getVelocity().multiply(2.0));

            Transformation finalTransform = block.getTransformation();
            finalTransform.getScale().set(r.nextFloat() * 1.5f + 0.8f);
            block.teleport(finalLocation);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!block.isValid()) return;
                    block.setInterpolationDuration(10);
                    finalTransform.getScale().set(0f);
                    block.setTransformation(finalTransform);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            block.remove();
                        }
                    }.runTaskLater(plugin, 11L);
                }
            }.runTaskLater(plugin, animationTicks - 10L);
        }

        world.spawnParticle(
                Particle.CLOUD,
                center,
                20,
                0.8, 0.5, 0.8,
                0.01
        );
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (!configManager.isTrimEnabled("flow")) return;
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }

    @EventHandler
    public void onEntityFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && isDashing.getOrDefault(player.getUniqueId(), false)) {
                event.setCancelled(true);
            } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL && player.getPersistentDataContainer().has(dashEndFallImmunityKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                player.getPersistentDataContainer().remove(dashEndFallImmunityKey);
            }
        }
    }

    public void cleanup() {
        for (UUID playerUUID : dashTasks.keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                deactivateDash(player, "Dash deactivated due to plugin reload.");
            }
        }
        dashTasks.clear();
        isDashing.clear();
    }
}
