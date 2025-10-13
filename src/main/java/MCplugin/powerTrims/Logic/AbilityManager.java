package MCplugin.powerTrims.Logic;

import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AbilityManager {

    private final Map<TrimPattern, Consumer<Player>> primaryAbilities = new HashMap<>();

    public void registerPrimaryAbility(TrimPattern trim, Consumer<Player> ability) {
        primaryAbilities.put(trim, ability);
    }

    public void activatePrimaryAbility(Player player) {
        TrimPattern equippedTrim = ArmourChecking.getEquippedTrim(player);
        if (equippedTrim != null && primaryAbilities.containsKey(equippedTrim)) {
            primaryAbilities.get(equippedTrim).accept(player);
        }
    }
}
