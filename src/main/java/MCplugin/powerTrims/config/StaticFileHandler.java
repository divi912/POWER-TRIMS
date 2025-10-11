package MCplugin.powerTrims.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class StaticFileHandler implements HttpHandler {

    private final JavaPlugin plugin;

    public StaticFileHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        String resourcePath = "web" + path;

        URL resource = plugin.getClass().getClassLoader().getResource(resourcePath);
        if (resource == null) {
            String response = "404 Not Found";
            plugin.getLogger().warning("Web server could not find resource: " + resourcePath);
            exchange.sendResponseHeaders(404, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            return;
        }

        try {
            URLConnection connection = resource.openConnection();
            String mimeType = connection.getContentType();

            // Set caching headers to prevent browser from using old versions of the file
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            exchange.getResponseHeaders().set("Content-Type", mimeType != null ? mimeType : "application/octet-stream");

            byte[] responseBytes;
            try (InputStream inputStream = connection.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                responseBytes = baos.toByteArray();
            }

            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error handling static file request: " + e.getMessage());
            exchange.sendResponseHeaders(500, -1); // Internal Server Error
        }
    }
}
