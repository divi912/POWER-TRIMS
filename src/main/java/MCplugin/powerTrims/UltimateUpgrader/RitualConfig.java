package MCplugin.powerTrims.UltimateUpgrader;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public class RitualConfig {
    private final int duration;
    private final List<ItemStack> materials;

    public RitualConfig(int duration, List<ItemStack> materials) {
        this.duration = duration;
        this.materials = materials;
    }

    public int getDuration() {
        return duration;
    }

    public List<ItemStack> getMaterials() {
        return materials;
    }
}
