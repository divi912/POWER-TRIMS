package MCplugin.powerTrims.Trims;

import MCplugin.powerTrims.Logic.*;
import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.integrations.WorldGuardIntegration;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CoastTrim implements Listener {
    private final JavaPlugin plugin;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;
    private final ConfigManager configManager;
    private final AbilityManager abilityManager;

    private final Map<UUID, List<BlockDisplay>> activeChains = new HashMap<>();

    private static final List<Material> WATER_MATERIALS = List.of(
            Material.PRISMARINE, Material.DARK_PRISMARINE, Material.SEA_LANTERN, Material.LAPIS_BLOCK
    );

    public CoastTrim(JavaPlugin plugin, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager, ConfigManager configManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.abilityManager = abilityManager;

        abilityManager.registerPrimaryAbility(TrimPattern.COAST, this::coastPrimary);
    }

    public void coastPrimary(Player player) {
        if (!configManager.isTrimEnabled("coast")) return;
        if (!ArmourChecking.hasFullTrimmedArmor(player, TrimPattern.COAST)) return;
        if (cooldownManager.isOnCooldown(player, TrimPattern.COAST)) return;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && !WorldGuardIntegration.canUseAbilities(player)) return;

        Location playerLoc = player.getLocation();
        World world = player.getWorld();


        int radius = configManager.getInt("coast.primary.water-burst-radius");
        int damage = configManager.getInt("coast.primary.water-burst-damage");
        long cooldown = configManager.getLong("coast.primary.water-burst-cooldown");
        world.playSound(playerLoc, Sound.ENTITY_GUARDIAN_ATTACK, 1.2f, 1.8f);
        world.playSound(playerLoc, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.5f, 1.2f);
        playani(player, radius);


        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(playerLoc, radius, radius, radius)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (target instanceof Player targetPlayer && trustManager.isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                    continue;
                }
                target.damage(damage, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 1));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 2));
                targets.add(target);
            }
        }

        if (!targets.isEmpty()) {
            startPullTask(player, targets);
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 0));

        cooldownManager.setCooldown(player, TrimPattern.COAST, cooldown);
        Messaging.sendTrimMessage(player, "Coast", ChatColor.DARK_AQUA, "You have used coast ability!");
    }


    private void playani(Player player, double radius) {
        Location center = player.getLocation();
        int particleCount = (int) (radius * 8);

        for (int i = 0; i < particleCount; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double angle = r.nextDouble(Math.PI * 2);
            Location startLoc = center.clone().add(Math.cos(angle) * radius, r.nextDouble(2.0), Math.sin(angle) * radius);
            Material material = WATER_MATERIALS.get(r.nextInt(WATER_MATERIALS.size()));

            BlockDisplay particle = center.getWorld().spawn(startLoc, BlockDisplay.class, bd -> {
                bd.setBlock(material.createBlockData());
                bd.setInterpolationDuration(20);
                bd.setInterpolationDelay(-1);
                Transformation t = bd.getTransformation();
                t.getScale().set(0.3f);
                bd.setTransformation(t);
            });

            Location endLoc = center.clone().add(r.nextDouble() - 0.5, 0.1, r.nextDouble() - 0.5);
            Transformation endTransform = particle.getTransformation();
            endTransform.getScale().set(0f);
            endTransform.getLeftRotation().rotateY((float) (Math.PI * 2));

            particle.teleport(endLoc);
            particle.setTransformation(endTransform);

            new BukkitRunnable() {
                @Override
                public void run() {
                    particle.remove();
                }
            }.runTaskLater(plugin, 21L);
        }
    }

    private void startPullTask(Player player, List<LivingEntity> targets) {
        final int CHAIN_LINKS = 8;

        BukkitRunnable pullTask = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks == 0) {
                    List<BlockDisplay> allChainLinks = new ArrayList<>();
                    for (LivingEntity target : targets) {
                        if (!target.isValid()) continue;
                        Location start = player.getEyeLocation();
                        for (int i = 0; i < CHAIN_LINKS; i++) {
                            BlockDisplay link = start.getWorld().spawn(start, BlockDisplay.class, bd -> {
                                bd.setBlock(Material.PRISMARINE.createBlockData());
                                bd.setBrightness(new BlockDisplay.Brightness(15, 15));
                                bd.setInterpolationDuration(10);
                                bd.setInterpolationDelay(-1);
                                Transformation t = bd.getTransformation();
                                t.getScale().set(0.2f);
                                bd.setTransformation(t);
                            });
                            allChainLinks.add(link);
                        }
                    }
                    activeChains.put(player.getUniqueId(), allChainLinks);
                }

                if (ticks++ > 80 || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                targets.removeIf(e -> !e.isValid() || e.isDead());
                if (targets.isEmpty()) {
                    this.cancel();
                    return;
                }

                Location playerPos = player.getEyeLocation();
                int chainIndex = 0;
                for (LivingEntity target : targets) {
                    if (target.getLocation().distanceSquared(playerPos) > 4) {
                        Vector pullDir = playerPos.toVector().subtract(target.getEyeLocation().toVector()).normalize().multiply(0.8);
                        target.setVelocity(pullDir);
                    }

                    Vector toTarget = target.getEyeLocation().toVector().subtract(playerPos.toVector());
                    for (int i = 0; i < CHAIN_LINKS; i++) {
                        if (chainIndex >= activeChains.get(player.getUniqueId()).size()) break;
                        BlockDisplay link = activeChains.get(player.getUniqueId()).get(chainIndex++);
                        if (!link.isValid()) continue;

                        double progress = (double) i / (CHAIN_LINKS - 1);
                        Location linkPos = playerPos.clone().add(toTarget.clone().multiply(progress));
                        link.teleport(linkPos);
                    }
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                if (activeChains.containsKey(player.getUniqueId())) {
                    for (BlockDisplay link : activeChains.get(player.getUniqueId())) {
                        if (link.isValid()) {
                            link.setInterpolationDuration(10);
                            Transformation t = link.getTransformation();
                            t.getScale().set(0f);
                            link.setTransformation(t);
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    link.remove();
                                }
                            }.runTaskLater(plugin, 11L);
                        }
                    }
                    activeChains.remove(player.getUniqueId());
                }
            }
        };

        pullTask.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler
    public void onOffhandPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            abilityManager.activatePrimaryAbility(event.getPlayer());
        }
    }
}