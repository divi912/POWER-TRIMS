package MCplugin.powerTrims.Logic;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Messaging {

    private static final String PLUGIN_PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "PowerTrims" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

    /**
     * Sends a standard, prefixed message from the plugin.
     * @param sender The CommandSender to send the message to.
     * @param message The message to send.
     */
    public static void sendPluginMessage(CommandSender sender, String message) {
        sender.sendMessage(PLUGIN_PREFIX + message);
    }

    /**
     * Sends a message prefixed with a specific trim's name and color.
     * @param player The player to send the message to.
     * @param trimName The name of the trim (e.g., "Silence").
     * @param color The color associated with the trim.
     * @param message The message to send.
     */
    public static void sendTrimMessage(Player player, String trimName, ChatColor color, String message) {
        String prefix = ChatColor.DARK_GRAY + "[" + color + trimName + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;
        player.sendMessage(prefix + message);
    }

    /**
     * Sends a standardized error message.
     * @param sender The CommandSender to send the error to.
     * @param message The error message.
     */
    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + "Error: " + message);
    }
}
