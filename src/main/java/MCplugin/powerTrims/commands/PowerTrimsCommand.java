package MCplugin.powerTrims.commands;

import MCplugin.powerTrims.Logic.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PowerTrimsCommand implements CommandExecutor {

    private final ConfigManager configManager;

    public PowerTrimsCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("powertrims.reload")) {
                configManager.reloadConfig();
                sender.sendMessage("PowerTrims config reloaded.");
            } else {
                sender.sendMessage("You don't have permission to do that.");
            }
            return true;
        }
        return false;
    }
}
