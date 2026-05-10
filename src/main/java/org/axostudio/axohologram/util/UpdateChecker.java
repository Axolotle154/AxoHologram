package org.axostudio.axohologram.util;

import org.axostudio.axohologram.AxoHologram;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class UpdateChecker {

    private static final String SPIGOT_API_URL =
            "https://api.spigotmc.org/legacy/update.php?resource=134707";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final AxoHologram plugin;

    public UpdateChecker(AxoHologram plugin) {
        this.plugin = plugin;
    }

    public void getVersion(final Consumer<String> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(SPIGOT_API_URL).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "AxoHologram-UpdateChecker");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String latestVersion = reader.readLine();
                    if (latestVersion != null && !latestVersion.isBlank()) {
                        consumer.accept(latestVersion.trim());
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Error checking updates: " + exception.getMessage());
            }
        });
    }
}
