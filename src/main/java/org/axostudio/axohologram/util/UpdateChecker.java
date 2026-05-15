package org.axostudio.axohologram.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.axostudio.axohologram.AxoHologram;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    private static final String PROJECT_ID = "v7Mdthjb";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final Pattern VERSION_TOKEN_PATTERN = Pattern.compile("[A-Za-z]+|\\d+");

    private final AxoHologram plugin;

    private volatile String latestVersion;
    private volatile String latestVersionUrl;
    private volatile boolean updateAvailable;

    public UpdateChecker(AxoHologram plugin) {
        this.plugin = plugin;
    }

    public void check() {
        reset();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            HttpURLConnection connection = null;
            try {
                String currentVersion = plugin.getPluginMeta().getVersion();
                String loader = Bukkit.getName().toLowerCase().contains("folia") ? "folia" : "paper";
                String minecraftVersion = Bukkit.getMinecraftVersion();

                plugin.getLogger().info("Checking updates from Modrinth...");

                String url = "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version"
                        + "?loaders=[%22" + loader + "%22]"
                        + "&game_versions=[%22" + minecraftVersion + "%22]"
                        + "&featured=true"
                        + "&include_changelog=false";

                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", plugin.getName() + "/" + currentVersion);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    plugin.getLogger().warning("Failed to check updates. Response code: " + responseCode);
                    return;
                }

                JsonArray versions;
                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                    versions = JsonParser.parseReader(reader).getAsJsonArray();
                }

                if (versions.isEmpty()) {
                    plugin.getLogger().warning("No compatible featured versions found on Modrinth.");
                    return;
                }

                JsonObject latest = versions.get(0).getAsJsonObject();
                latestVersion = latest.get("version_number").getAsString();
                String versionId = latest.get("id").getAsString();
                latestVersionUrl = "https://modrinth.com/project/" + PROJECT_ID + "/version/" + versionId;

                int comparison = compareVersions(latestVersion, currentVersion);
                if (comparison <= 0) {
                    if (comparison < 0) {
                        plugin.getLogger().info("You are running a local version newer than the latest featured Modrinth build: "
                                + currentVersion + " (latest featured: " + latestVersion + ")");
                    } else {
                        plugin.getLogger().info("You are running the latest version: " + currentVersion);
                    }
                    return;
                }

                updateAvailable = true;
                plugin.getLogger().warning(" ");
                plugin.getLogger().warning("========================================");
                plugin.getLogger().warning("A new update is available!");
                plugin.getLogger().warning("Current version: " + currentVersion);
                plugin.getLogger().warning("Latest version: " + latestVersion);
                plugin.getLogger().warning("Download:");
                plugin.getLogger().warning(latestVersionUrl);
                plugin.getLogger().warning("========================================");
                plugin.getLogger().warning(" ");
            } catch (Exception exception) {
                plugin.getLogger().warning("Error while checking updates: " + exception.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public void notifyPlayer(Player player) {
        if (!updateAvailable || player == null || !player.isOnline()) {
            return;
        }
        if (!player.hasPermission("axohologram.update.notify")) {
            return;
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("AxoHologram Update", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("A new update is available.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Latest version: ", NamedTextColor.GRAY)
                .append(Component.text(latestVersion, NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Download: ", NamedTextColor.GRAY)
                .append(Component.text(latestVersionUrl, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(latestVersionUrl))));
        player.sendMessage(Component.empty());
    }

    public void reset() {
        latestVersion = null;
        latestVersionUrl = null;
        updateAvailable = false;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getLatestVersionUrl() {
        return latestVersionUrl;
    }

    private static int compareVersions(String leftVersion, String rightVersion) {
        List<String> leftTokens = tokenizeVersion(leftVersion);
        List<String> rightTokens = tokenizeVersion(rightVersion);
        int max = Math.max(leftTokens.size(), rightTokens.size());
        for (int index = 0; index < max; index++) {
            if (index >= leftTokens.size()) {
                return -compareMissingToRemaining(rightTokens, index);
            }
            if (index >= rightTokens.size()) {
                return compareMissingToRemaining(leftTokens, index);
            }

            String leftToken = leftTokens.get(index);
            String rightToken = rightTokens.get(index);
            if (leftToken.equals(rightToken)) {
                continue;
            }

            boolean leftNumeric = isNumericToken(leftToken);
            boolean rightNumeric = isNumericToken(rightToken);
            if (leftNumeric && rightNumeric) {
                return compareNumericTokens(leftToken, rightToken);
            }
            if (!leftNumeric && !rightNumeric) {
                int leftRank = qualifierRank(leftToken);
                int rightRank = qualifierRank(rightToken);
                if (leftRank != rightRank) {
                    return Integer.compare(leftRank, rightRank);
                }
                return leftToken.compareTo(rightToken);
            }

            return leftNumeric ? 1 : -1;
        }
        return 0;
    }

    private static int compareMissingToRemaining(List<String> tokens, int startIndex) {
        for (int index = startIndex; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (isNumericToken(token)) {
                int comparison = compareNumericTokens(token, "0");
                if (comparison != 0) {
                    return comparison;
                }
                continue;
            }

            int rank = qualifierRank(token);
            if (rank != 0) {
                return rank;
            }
        }
        return 0;
    }

    private static List<String> tokenizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return List.of();
        }

        String normalized = version.trim().toLowerCase(Locale.ROOT);
        int metadataIndex = normalized.indexOf('+');
        if (metadataIndex >= 0) {
            normalized = normalized.substring(0, metadataIndex);
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = VERSION_TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static boolean isNumericToken(String token) {
        return !token.isEmpty() && Character.isDigit(token.charAt(0));
    }

    private static int compareNumericTokens(String leftToken, String rightToken) {
        String normalizedLeft = stripLeadingZeros(leftToken);
        String normalizedRight = stripLeadingZeros(rightToken);
        if (normalizedLeft.length() != normalizedRight.length()) {
            return Integer.compare(normalizedLeft.length(), normalizedRight.length());
        }
        return normalizedLeft.compareTo(normalizedRight);
    }

    private static String stripLeadingZeros(String token) {
        int index = 0;
        while (index < token.length() - 1 && token.charAt(index) == '0') {
            index++;
        }
        return token.substring(index);
    }

    private static int qualifierRank(String token) {
        return switch (token) {
            case "snapshot", "dev", "nightly", "canary" -> -5;
            case "pre", "preview" -> -4;
            case "alpha", "a" -> -3;
            case "beta", "b" -> -2;
            case "rc", "cr" -> -1;
            case "release", "final", "ga", "stable" -> 0;
            default -> -1;
        };
    }
}
