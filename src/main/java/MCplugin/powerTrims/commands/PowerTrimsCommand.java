package MCplugin.powerTrims.commands;

import MCplugin.powerTrims.config.ConfigManager;
import MCplugin.powerTrims.Logic.PersistentTrustManager;
import MCplugin.powerTrims.Logic.TrimCooldownManager;
import MCplugin.powerTrims.PowerTrimss;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PowerTrimsCommand implements CommandExecutor {

    private final PowerTrimss plugin;
    private final ConfigManager configManager;
    private final TrimCooldownManager cooldownManager;
    private final PersistentTrustManager trustManager;

    public PowerTrimsCommand(PowerTrimss plugin, ConfigManager configManager, TrimCooldownManager cooldownManager, PersistentTrustManager trustManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cooldownManager = cooldownManager;
        this.trustManager = trustManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
            case "resetcooldowns":
                handleResetCooldowns(sender);
                break;
            case "trust":
                handleTrust(sender, args);
                break;
            case "untrust":
                handleUntrust(sender, args);
                break;
            case "trustlist":
                handleTrustList(sender);
                break;
            case "panel":
                handlePanel(sender);
                break;
            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    private void handlePanel(CommandSender sender) {
        if (!sender.hasPermission("powertrims.panel")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }

        String configAddress = plugin.getConfig().getString("web-address", "0.0.0.0");
        int port = plugin.getConfig().getInt("web-port", 8080);

        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (configAddress.equals("0.0.0.0")) {
                player.sendMessage(ChatColor.RED + "The web panel is not configured for public access.");
                player.sendMessage(ChatColor.YELLOW + "Please ask a server administrator to set the 'web-address' in the config.yml.");
            } else {
                String url = "http://" + configAddress + ":" + port;
                TextComponent message = new TextComponent(ChatColor.GOLD + "Click here to open the PowerTrims web panel.");
                message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Opens " + url + " in your browser")));
                player.spigot().sendMessage(message);
            }
        } else {
            // Console sender
            String url = "http://localhost:" + port;
            sender.sendMessage(ChatColor.GREEN + "PowerTrims web panel URL: " + ChatColor.YELLOW + url);
            sender.sendMessage(ChatColor.GRAY + "(Use this URL in a browser on the same machine as the server.)");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("powertrims.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }
        plugin.reloadPlugin();
        sender.sendMessage(ChatColor.GREEN + "PowerTrims has been reloaded.");
    }

    private void handleResetCooldowns(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("powertrims.resetcooldowns")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }
        cooldownManager.resetAllCooldowns(player);
        player.sendMessage(ChatColor.GREEN + "All your trim cooldowns have been reset.");
    }

    private void handleTrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /powertrims trust <player>");
            return;
        }
        Player player = (Player) sender;
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        trustManager.trustPlayer(player.getUniqueId(), targetPlayer.getUniqueId(), sender);
    }

    private void handleUntrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /powertrims untrust <player>");
            return;
        }
        Player player = (Player) sender;
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        trustManager.untrustPlayer(player.getUniqueId(), targetPlayer.getUniqueId(), sender);
    }

    private void handleTrustList(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }
        trustManager.showTrustList(((Player) sender).getUniqueId(), sender);
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- PowerTrims Help ---");
        sender.sendMessage(ChatColor.AQUA + "/powertrims panel" + ChatColor.GRAY + " - Opens the web configuration panel.");
        sender.sendMessage(ChatColor.AQUA + "/powertrims reload" + ChatColor.GRAY + " - Reloads the config.");
        sender.sendMessage(ChatColor.AQUA + "/powertrims resetcooldowns" + ChatColor.GRAY + " - Resets all your cooldowns.");
        sender.sendMessage(ChatColor.AQUA + "/powertrims trust <player>" + ChatColor.GRAY + " - Trust a player.");
        sender.sendMessage(ChatColor.AQUA + "/powertrims untrust <player>" + ChatColor.GRAY + " - Untrust a player.");
        sender.sendMessage(ChatColor.AQUA + "/powertrims trustlist" + ChatColor.GRAY + " - View your trusted players.");
    }
}
