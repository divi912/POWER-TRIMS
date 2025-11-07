package MCplugin.powerTrims.UltimateUpgrader;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;

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
    private final List<BlockDisplay> pillarCrystals = new ArrayList<>();
    private ArmorStand hologram;

    public RitualAnimation(JavaPlugin plugin, Location center, ItemStack[] armorSet, List<ItemStack> materials) {
        this.plugin = plugin;
        this.center = center.clone().add(0, 0.2, 0);
        this.armorSet = armorSet;
        this.materials = materials;
    }

    public void start() {
        World world = center.getWorld();

        hologram = world.spawn(center.clone().add(0, 5.5, 0), ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setCustomNameVisible(true);
        });
        ritualEntities.add(hologram);

        BlockDisplay centralPedestal = world.spawn(center.clone(), BlockDisplay.class, bd -> {
            bd.setBlock(Material.CRYING_OBSIDIAN.createBlockData());
            bd.setBrightness(new Display.Brightness(15, 15));
            Transformation t = bd.getTransformation();
            t.getScale().set(0.8f, 0.3f, 0.8f);
            t.getLeftRotation().identity();
            bd.setTransformation(t);
        });
        ritualEntities.add(centralPedestal);

        double pillarDistance = 7.0;
        createPillar(center.clone().add(pillarDistance, 0, 0));
        createPillar(center.clone().add(-pillarDistance, 0, 0));
        createPillar(center.clone().add(0, 0, pillarDistance));
        createPillar(center.clone().add(0, 0, -pillarDistance));

        float[] armorYOffsets = {2.5f, 2.0f, 1.5f, 1.0f};
        for (int i = 0; i < armorSet.length; i++) {
            int finalI = i;
            ItemDisplay armorPiece = world.spawn(center.clone().add(0, armorYOffsets[i], 0), ItemDisplay.class, id -> {
                id.setItemStack(armorSet[finalI]);
                id.setBrightness(new Display.Brightness(15, 15));
                Transformation t = id.getTransformation();
                t.getScale().set(1.2f);
                id.setTransformation(t);
            });
            ritualEntities.add(armorPiece);
        }
        setupOrbitingMaterials();
        world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 2.0f, 0.5f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.7f);

        animationTask = new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                angle += Math.PI / 100;
                drawRuneCircles(angle);

                animateOrbitingMaterials(angle);

                for (Entity e : ritualEntities) {
                    if (e instanceof ItemDisplay id && isArmor(id.getItemStack())) {
                        Transformation t = id.getTransformation();
                        t.getLeftRotation().set(new Quaternionf().rotateY((float) -angle * 0.5f));
                        id.setTransformation(t);
                    }
                }

                for (BlockDisplay crystal : pillarCrystals) {
                    float pulse = (float) (0.6 + Math.sin(angle * 4) * 0.2);
                    Transformation t = crystal.getTransformation();
                    t.getScale().set(pulse);
                    crystal.setTransformation(t);
                }

                if (this.getTaskId() % 100 == 0) world.playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 0.8f);

            }
        }.runTaskTimer(plugin, 0L, 1L);
    }


    private void createPillar(Location location) {
        BlockDisplay pillarBase = location.getWorld().spawn(location, BlockDisplay.class, bd -> {
            bd.setBlock(Material.OBSIDIAN.createBlockData());
            bd.setBrightness(new Display.Brightness(10, 10));
            Transformation t = bd.getTransformation();
            t.getScale().set(0.6f, 2.5f, 0.6f);
            t.getLeftRotation().identity();
            bd.setTransformation(t);
        });
        ritualEntities.add(pillarBase);

        BlockDisplay crystal = location.getWorld().spawn(location.clone().add(0, 2.5, 0), BlockDisplay.class, bd -> {
            bd.setBlock(Material.AMETHYST_BLOCK.createBlockData());
            bd.setBrightness(new Display.Brightness(15, 15));
            bd.setGlowing(true);
            Transformation t = bd.getTransformation();
            t.getScale().set(0.6f);
            t.getLeftRotation().identity();
            bd.setTransformation(t);
        });
        pillarCrystals.add(crystal);
        ritualEntities.add(crystal);
    }

    private void drawRuneCircles(double currentAngle) {
        World world = center.getWorld();
        int points = 60; // Reduced from 120

        double radius1 = 5.5;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = Math.cos(angle + currentAngle) * radius1;
            double z = Math.sin(angle + currentAngle) * radius1;
            world.spawnParticle(Particle.DUST, center.clone().add(x, 0.1, z), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.RED, 1.0f));
        }

        double radius2 = 4.0;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = Math.cos(angle - currentAngle * 1.2) * radius2;
            double z = Math.sin(angle - currentAngle * 1.2) * radius2;
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(x, 0.15, z), 1, 0, 0, 0, 0);
        }
    }

    private void setupOrbitingMaterials() {
        World world = center.getWorld();
        int materialsPerRing = (materials.size() + 1) / 2;

        for (int i = 0; i < materials.size(); i++) {
            ItemStack materialStack = materials.get(i);
            boolean isOuterRing = i < materialsPerRing;
            double radius = isOuterRing ? 6.0 : 3.5;
            double yOffset = isOuterRing ? 2.2 : 1.7;

            Location spawnLoc = center.clone().add(radius, yOffset, 0);

            Display display = materialStack.getType().isBlock() ?
                    world.spawn(spawnLoc, BlockDisplay.class, d -> {
                        d.setBlock(materialStack.getType().createBlockData());
                        Transformation t = d.getTransformation();
                        t.getScale().set(0.4f);
                        t.getLeftRotation().identity();
                        d.setTransformation(t);
                    }) :
                    world.spawn(spawnLoc, ItemDisplay.class, d -> {
                        d.setItemStack(materialStack);
                        d.getTransformation().getScale().set(0.6f);
                    });

            display.setBrightness(new Display.Brightness(15, 15));
            orbitingDisplays.add(display);
            ritualEntities.add(display);
        }
    }

    private void animateOrbitingMaterials(double currentAngle) {
        int materialsPerRing = (orbitingDisplays.size() + 1) / 2;
        for (int i = 0; i < orbitingDisplays.size(); i++) {
            Display display = orbitingDisplays.get(i);
            boolean isOuterRing = i < materialsPerRing;

            double radius = isOuterRing ? 6.0 : 3.5;
            double yOffset = isOuterRing ? 2.2 : 1.7;
            double speed = isOuterRing ? 1.0 : -1.2;

            int ringIndex = isOuterRing ? i : i - materialsPerRing;
            int ringSize = isOuterRing ? materialsPerRing : orbitingDisplays.size() - materialsPerRing;

            double orbitAngle = (2 * Math.PI / ringSize) * ringIndex + (currentAngle * speed);
            double bob = Math.sin(currentAngle * 2 + i) * 0.15;

            Location newLoc = center.clone().add(radius * Math.cos(orbitAngle), yOffset + bob, radius * Math.sin(orbitAngle));
            display.teleport(newLoc);

            Transformation t = display.getTransformation();
            t.getLeftRotation().set(new Quaternionf().rotateY((float) currentAngle * 2));
            display.setTransformation(t);
        }
    }

    public void stop() {
        if (animationTask != null) animationTask.cancel();

        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1.5, 0), 3, 0.5, 0.5, 0.5);

        ritualEntities.forEach(Entity::remove);
        ritualEntities.clear();
        orbitingDisplays.clear();
        pillarCrystals.clear();
    }

    // Helper methods
    public void updateHologram(int timeRemaining) {
        if (hologram != null && hologram.isValid()) {
            Component text = Component.text("◆ ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text("RITUAL IN PROGRESS", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" ◆", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text(timeRemaining + "s", NamedTextColor.WHITE, TextDecoration.BOLD));
            hologram.customName(text);
        }
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.endsWith("_HELMET") || typeName.endsWith("_CHESTPLATE") || typeName.endsWith("_LEGGINGS") || typeName.endsWith("_BOOTS");
    }
}