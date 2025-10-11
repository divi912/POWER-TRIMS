package MCplugin.powerTrims.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import MCplugin.powerTrims.PowerTrimss;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigHandler implements HttpHandler {

    private final JavaPlugin plugin;
    private final Gson gson = new Gson();

    public ConfigHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // TODO: Add security checks (e.g., token authentication)

        if ("GET".equals(exchange.getRequestMethod())) {
            handleGet(exchange);
        } else if ("POST".equals(exchange.getRequestMethod())) {
            handlePost(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }

    private Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                map.put(key, sectionToMap((ConfigurationSection) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String jsonResponse;
        try {
            FileConfiguration config = plugin.getConfig();
            Map<String, Object> configAsMap = sectionToMap(config);
            jsonResponse = gson.toJson(configAsMap);
        } catch (Exception e) {
            plugin.getLogger().severe("Error serializing config to JSON: " + e.getMessage());
            exchange.sendResponseHeaders(500, -1);
            return;
        }

        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            FileConfiguration config = plugin.getConfig();
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> updatedConfig = gson.fromJson(reader, type);

            for (Map.Entry<String, Object> entry : updatedConfig.entrySet()) {
                config.set(entry.getKey(), entry.getValue());
            }

            plugin.saveConfig();

            // Use Bukkit's scheduler to run the reload on the main thread for safety
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin instanceof PowerTrimss) {
                    ((PowerTrimss) plugin).reloadPlugin();
                }
            });

            String response = "{\"message\": \"Configuration updated successfully!\"}";
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error updating config from web interface: " + e.getMessage());
            exchange.sendResponseHeaders(500, -1); // Internal Server Error
        }
    }
}
