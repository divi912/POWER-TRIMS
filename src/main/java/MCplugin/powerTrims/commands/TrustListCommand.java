package MCplugin.powerTrims.commands;

import MCplugin.powerTrims.Logic.PersistentTrustManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TrustListCommand implements CommandExecutor {

    private final PersistentTrustManager trustManager;

    public TrustListCommand(PersistentTrustManager trustManager) {
        this.trustManager = trustManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        trustManager.showTrustList(((Player) sender).getUniqueId(), sender);
        return true;
    }
}
