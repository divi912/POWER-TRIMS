package MCplugin.powerTrims.Logic;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ConfigWebServer {

    private final JavaPlugin plugin;
    private HttpServer server;

    public ConfigWebServer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            // TODO: Make port and address configurable
            server = HttpServer.create(new InetSocketAddress("localhost", 8080), 0);

            // Create handlers for API and file serving
            server.createContext("/api/config", new ConfigHandler(plugin));
            server.createContext("/", new StaticFileHandler(plugin));

            server.setExecutor(null); // creates a default executor
            server.start();
            plugin.getLogger().info("Config web server started on http://localhost:8080");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not start config web server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Config web server stopped.");
        }
    }
}
