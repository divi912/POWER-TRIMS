package MCplugin.powerTrims.UltimateUpgrader;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.List;

public class RitualAnimation {

    private final JavaPlugin plugin;
    private final Location center;
    private final ItemStack[] armorSet;
    private final List<ItemStack> materials;

    private BukkitTask animationTask;
    private final List<Entity> ritualEntities = new ArrayList<>();
    private final List<Display> orbitingDisplays = new ArrayList<>();
    private ArmorStand hologram;

    public RitualAnimation(JavaPlugin plugin, Location center, ItemStack[] armorSet, List<ItemStack> materials) {
        this.plugin = plugin;
        this.center = center.clone().add(0, 0.5, 0); // Center of the ritual
        this.armorSet = armorSet;
        this.materials = materials;
    }

    public void start() {
        // Hologram
        hologram = center.getWorld().spawn(center.clone().add(0, 2.5, 0), ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setCustomNameVisible(true);
        });
        ritualEntities.add(hologram);

        // Simulated Beacon Beam
        BlockDisplay beaconBeam = center.getWorld().spawn(center.clone().add(0, 25, 0), BlockDisplay.class, bd -> {
            bd.setBlock(Material.RED_STAINED_GLASS.createBlockData());
            Transformation t = bd.getTransformation();
            t.getScale().set(0.15f, 50f, 0.15f);
            bd.setTransformation(t);
            bd.setGlowing(true);
            bd.setGlowColorOverride(org.bukkit.Color.RED);
        });
        ritualEntities.add(beaconBeam);

        // Floating Armor
        float[] armorYOffsets = {1.8f, 1.1f, 0.5f, 0.0f}; // Head, Chest, Legs, Boots
        for (int i = 0; i < armorSet.length; i++) {
            int finalI = i;
            ItemDisplay armorPiece = center.getWorld().spawn(center.clone().add(0, armorYOffsets[i], 0), ItemDisplay.class, id -> {
                id.setItemStack(armorSet[finalI]);
                Transformation t = id.getTransformation();
                t.getScale().set(1.5f);
                id.setTransformation(t);
            });
            ritualEntities.add(armorPiece);
        }

        // Orbiting Materials
        for (ItemStack materialStack : materials) {
            Location spawnLoc = center.clone().add(2.5, 1, 0);
            if (materialStack.getType().isBlock()) {
                orbitingDisplays.add(center.getWorld().spawn(spawnLoc, BlockDisplay.class, d -> {
                    d.setBlock(materialStack.getType().createBlockData());
                    d.getTransformation().getScale().set(0.5f);
                }));
            } else {
                orbitingDisplays.add(center.getWorld().spawn(spawnLoc, ItemDisplay.class, d -> {
                    d.setItemStack(materialStack);
                    d.getTransformation().getScale().set(0.7f);
                }));
            }
        }
        ritualEntities.addAll(orbitingDisplays);

        // Animation Task
        animationTask = new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                angle += Math.PI / 64;

                // Orbiting materials
                for (int i = 0; i < orbitingDisplays.size(); i++) {
                    Display display = orbitingDisplays.get(i);
                    double orbitAngle = (2 * Math.PI / orbitingDisplays.size()) * i + angle;
                    double radius = 2.5;
                    Location newLoc = center.clone().add(radius * Math.cos(orbitAngle), 1.0, radius * Math.sin(orbitAngle));
                    display.teleport(newLoc);
                    Transformation t = display.getTransformation();
                    t.getLeftRotation().rotateY(0.05f);
                    display.setTransformation(t);
                }

                // Pulse beacon
                float pulse = (float) (0.15 + Math.sin(angle * 4) * 0.05);
                Transformation t = beaconBeam.getTransformation();
                t.getScale().set(pulse, 50f, pulse);
                beaconBeam.setTransformation(t);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void updateHologram(int timeRemaining) {
        if (hologram != null && hologram.isValid()) {
            hologram.customName(Component.text("Ritual in Progress... ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(timeRemaining + "s", NamedTextColor.WHITE)));
        }
    }

    public void stop() {
        if (animationTask != null) {
            animationTask.cancel();
        }
        ritualEntities.forEach(Entity::remove);
        ritualEntities.clear();
        orbitingDisplays.clear();
    }
}
