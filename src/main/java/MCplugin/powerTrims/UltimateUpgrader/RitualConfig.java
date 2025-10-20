package MCplugin.powerTrims.UltimateUpgrader;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public class RitualConfig {
    private final int duration;
    private final List<ItemStack> materials;
    private final int limit;
    private final boolean enabled;

    public RitualConfig(int duration, List<ItemStack> materials, int limit, boolean enabled) {
        this.duration = duration;
        this.materials = materials;
        this.limit = limit;
        this.enabled = enabled;
    }

    public int getDuration() {
        return duration;
    }

    public List<ItemStack> getMaterials() {
        return materials;
    }

    public int getLimit() {
        return limit;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
