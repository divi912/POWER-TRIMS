// CommandKeys / Macro Support 
// Or just ppl who are weird and want to use /utp
package MCplugin.powerTrims.commands;
import MCplugin.powerTrims.Logic.AbilityManager;
import org.bukkit.ChatColor;
import java.util.HashMap;
import MCplugin.powerTrims.Logic.ArmourChecking;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.command.Command;
import MCplugin.powerTrims.PowerTrimss;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MacroSupport implements CommandExecutor {
    private final PowerTrimss plugin;
    private final AbilityManager abilityManager;
    private final ArmourChecking armourChecking;
    private final Map<TrimPattern, Consumer<Player>> primaryAbilities = new HashMap<>();

    public MacroSupport(PowerTrimss plugin, AbilityManager abilityManager, ArmourChecking armourChecking) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.armourChecking = armourChecking;

    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        Player player = (Player) sender;
        TrimPattern equippedTrim = armourChecking.getEquippedTrim(player);
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players."); // yea im gonna use a trim power as console
            return true;
        }
        //if (equippedTrim != null && primaryAbilities.containsKey(equippedTrim)) {
        if (equippedTrim == null) {
            player.sendMessage(ChatColor.RED + "No Equipped Trim!");
            
        // basic asf
        } else {
            abilityManager.activatePrimaryAbility(player);
            player.sendMessage(ChatColor.GREEN + "Used Trim Power!");
        }
       
        

        return true;
    }
    
}
