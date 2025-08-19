package MCplugin.powerTrims.commands;

import MCplugin.powerTrims.Logic.TrimCooldownManager;
import MCplugin.powerTrims.PowerTrimss;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetCooldownsCommand implements CommandExecutor {

    private final PowerTrimss plugin;

    public ResetCooldownsCommand(PowerTrimss plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("powertrims.resetcooldowns")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        TrimCooldownManager cooldownManager = plugin.getCooldownManager();
        cooldownManager.resetAllCooldowns(player);
        player.sendMessage(ChatColor.GREEN + "All your trim cooldowns have been reset.");

        return true;
    }
}
