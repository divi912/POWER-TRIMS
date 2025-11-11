package MCplugin.powerTrims.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin; // <-- ADD THIS IMPORT
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.entity.Player;

public class WorldGuardIntegration {

    public static StateFlag USE_TRIM_ABILITIES;

    public static void registerFlags() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        final String flagName = "powertrims-abilities";

        try {
            StateFlag flag = new StateFlag(flagName, true);
            registry.register(flag);
            USE_TRIM_ABILITIES = flag;
        } catch (Exception e) {
            Flag<?> existing = registry.get(flagName);
            if (existing instanceof StateFlag) {
                USE_TRIM_ABILITIES = (StateFlag) existing;
            } else {
                System.err.println("[PowerTrims] WorldGuard flag '" + flagName + "' already exists but is not a StateFlag. This is a critical conflict.");
            }
        }
    }

    public static boolean canUseAbilities(Player player) {
        if (USE_TRIM_ABILITIES == null) {
            return true;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        return query.testState(
                BukkitAdapter.adapt(player.getLocation()),
                WorldGuardPlugin.inst().wrapPlayer(player),
                USE_TRIM_ABILITIES
        );
    }
}