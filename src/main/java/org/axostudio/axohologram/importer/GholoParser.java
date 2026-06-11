package org.axostudio.axohologram.importer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.visibility.VisibilityMode;
import org.axostudio.axohologram.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class GholoParser {

    private static final Pattern PLACEHOLDER_API_PATTERN = Pattern.compile("%[^%\\s]+%");

    private final AxoHologram plugin;
    private final File sourceFile;
    private final File configFile;

    public GholoParser(AxoHologram plugin) {
        this.plugin = plugin;
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        File sourceFolder = new File(pluginsFolder == null ? new File("plugins") : pluginsFolder, "GHolo");
        this.sourceFile = new File(sourceFolder, "data/data.db");
        this.configFile = new File(sourceFolder, "config.yml");
    }

    public boolean isAvailable() {
        return sourceFile.isFile() && isSqliteDriverAvailable();
    }

    public Map<String, SourceHologram> parseAll() {
        Map<String, SourceHologram> holograms = new LinkedHashMap<>();
        if (!sourceFile.isFile()) {
            return holograms;
        }
        if (!isSqliteDriverAvailable()) {
            plugin.getLogger().warning("[IMPORT] GHolo data.db was found, but no SQLite JDBC driver is available.");
            return holograms;
        }

        Map<String, String> symbols = readSymbols();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT uuid, id, location, data FROM gholo_holo ORDER BY id COLLATE NOCASE")) {
            while (rows.next()) {
                try {
                    SourceHologram hologram = parseHologram(connection, rows, symbols);
                    holograms.put(hologram.name(), hologram);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING, "[IMPORT] Skipping corrupt GHolo hologram \"" + rows.getString("id") + "\".", exception);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "[IMPORT] Could not read GHolo data.db.", exception);
        }
        return holograms;
    }

    public SourceHologram parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Map<String, SourceHologram> holograms = parseAll();
        SourceHologram exact = holograms.get(name);
        if (exact != null) {
            return exact;
        }
        for (SourceHologram hologram : holograms.values()) {
            if (hologram.name().equalsIgnoreCase(name)) {
                return hologram;
            }
        }
        return null;
    }

    private SourceHologram parseHologram(Connection connection, ResultSet row, Map<String, String> symbols) throws SQLException {
        String uuid = row.getString("uuid");
        String name = row.getString("id");
        JsonObject locationData = parseJsonObject(row.getString("location"));
        JsonObject data = parseJsonObject(row.getString("data"));
        ParsedGholoLocation parsedLocation = readLocation(locationData);

        List<SourceLine> lines = readLines(connection, uuid, symbols);
        if (lines.isEmpty()) {
            lines = List.of(new SourceLine(LineType.TEXT, "", null, null, new Vector(), null, null, null));
        }

        float scale = readFloat(data, 1.0F, "scale");
        String displayAnimation = plugin.getConfigManager().getConfig().getBoolean("importer.import-animations", true)
                ? readString(data, "displayAnimation", "display-animation", "animation")
                : null;

        return new SourceHologram(
                "GHolo",
                name,
                parsedLocation.worldName(),
                parsedLocation.location(),
                readBoolean(data, true, "enabled", "visible"),
                readInt(data, -1, "visibilityDistance", "visibility-distance", "viewDistance", "view-distance", "range"),
                VisibilityMode.ALL,
                Billboard.fromString(readString(data, "billboard")),
                readFloat(data, scale, "scaleX", "scale-x"),
                readFloat(data, scale, "scaleY", "scale-y"),
                readFloat(data, scale, "scaleZ", "scale-z"),
                new Vector(),
                readFloat(data, 0.0F, "shadowRadius", "shadow-radius"),
                readFloat(data, 1.0F, "shadowStrength", "shadow-strength"),
                readColor(data),
                readBoolean(data, false, "textShadow", "text-shadow"),
                readBoolean(data, false, "seeThrough", "see-through"),
                readAlignment(data),
                readLong(data, -1L,
                        "updateTextInterval",
                        "update-text-interval",
                        "updateInterval",
                        "update-interval",
                        "update_text_interval",
                        "update_interval",
                        "text_update_interval",
                        "text.update-interval",
                        "text.update_interval",
                        "text.updateTextInterval",
                        "text.updateInterval"),
                displayAnimation,
                List.of(lines)
        );
    }

    private List<SourceLine> readLines(Connection connection, String hologramUuid, Map<String, String> symbols) throws SQLException {
        List<GholoRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT position, content, `offset`, data FROM gholo_holo_row WHERE holo_uuid = ? ORDER BY position")) {
            statement.setString(1, hologramUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new GholoRow(
                            resultSet.getInt("position"),
                            resultSet.getString("content"),
                            parseJsonObject(resultSet.getString("offset")),
                            parseJsonObject(resultSet.getString("data"))
                    ));
                }
            }
        }

        rows.sort(Comparator.comparingInt(GholoRow::position));
        List<SourceLine> lines = new ArrayList<>(rows.size());
        for (GholoRow row : rows) {
            Vector offset = readLineOffset(row.offset());
            lines.add(new SourceLine(
                    LineType.TEXT,
                    importText(applySymbols(row.content(), symbols)),
                    null,
                    null,
                    offset,
                    null,
                    null,
                    readString(row.data(), "permission", "viewPermission", "view-permission")
            ));
        }
        return List.copyOf(lines);
    }

    private Vector readLineOffset(JsonObject offset) {
        double x = readDouble(offset, 0.0D, "x");
        double z = readDouble(offset, 0.0D, "z");
        if (x == 0.0D && z == 0.0D) {
            return new Vector();
        }
        return new Vector(x, 0.0D, z);
    }

    private ParsedGholoLocation readLocation(JsonObject object) {
        String worldName = readString(object, "world", "worldName", "world-name");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("missing world");
        }

        Location location = new Location(
                Bukkit.getWorld(worldName),
                readDouble(object, 0.0D, "x"),
                readDouble(object, 0.0D, "y"),
                readDouble(object, 0.0D, "z"),
                (float) readDouble(object, 0.0D, "yaw"),
                (float) readDouble(object, 0.0D, "pitch")
        );
        return new ParsedGholoLocation(worldName, location);
    }

    private Map<String, String> readSymbols() {
        if (!configFile.isFile()) {
            return Map.of();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection symbolsSection = config.getConfigurationSection("Options.Symbols");
        if (symbolsSection == null) {
            return Map.of();
        }

        Map<String, String> symbols = new LinkedHashMap<>();
        for (String key : symbolsSection.getKeys(false)) {
            String value = symbolsSection.getString(key);
            if (value != null) {
                symbols.put(key, value);
            }
        }
        return symbols;
    }

    private String applySymbols(String text, Map<String, String> symbols) {
        String value = text == null ? "" : text;
        for (Map.Entry<String, String> entry : symbols.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        return value;
    }

    private String importText(String text) {
        String value = text == null ? "" : text;
        if (plugin.getConfigManager().getConfig().getBoolean("importer.import-placeholders", true)) {
            return value;
        }
        return PLACEHOLDER_API_PATTERN.matcher(value).replaceAll("");
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + sourceFile.getAbsolutePath().replace('\\', '/'));
    }

    private boolean isSqliteDriverAvailable() {
        try {
            Class.forName("org.sqlite.JDBC");
            return true;
        } catch (ClassNotFoundException ignored) {
            try {
                DriverManager.getDriver("jdbc:sqlite:");
                return true;
            } catch (SQLException exception) {
                return false;
            }
        }
    }

    private JsonObject parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JsonObject();
        }

        try {
            JsonElement element = JsonParser.parseString(raw);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    private String readString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull()) {
                return element.getAsString();
            }
        }
        return null;
    }

    private boolean readBoolean(JsonObject object, boolean fallback, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull()) {
                return element.getAsBoolean();
            }
        }
        return fallback;
    }

    private int readInt(JsonObject object, int fallback, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull()) {
                return element.getAsInt();
            }
        }
        return fallback;
    }

    private long readLong(JsonObject object, long fallback, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull()) {
                return readLong(element, fallback);
            }
        }
        return fallback;
    }

    private long readLong(JsonElement element, long fallback) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        }

        String normalized = element.getAsString().trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("default")) {
            return fallback;
        }
        try {
            if (normalized.endsWith("milliseconds")) {
                return millisecondsToTicks(normalized.substring(0, normalized.length() - "milliseconds".length()));
            }
            if (normalized.endsWith("millisecond")) {
                return millisecondsToTicks(normalized.substring(0, normalized.length() - "millisecond".length()));
            }
            if (normalized.endsWith("ms")) {
                return millisecondsToTicks(normalized.substring(0, normalized.length() - 2));
            }
            if (normalized.endsWith("seconds")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - "seconds".length()));
            }
            if (normalized.endsWith("second")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - "second".length()));
            }
            if (normalized.endsWith("secs")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - 4));
            }
            if (normalized.endsWith("sec")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - 3));
            }
            if (normalized.endsWith("s")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - 1));
            }
            if (normalized.endsWith("ticks")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 5).trim());
            }
            if (normalized.endsWith("tick")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 4).trim());
            }
            if (normalized.endsWith("t")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1).trim());
            }
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long secondsToTicks(String rawSeconds) {
        double seconds = Double.parseDouble(rawSeconds.trim());
        return Double.isFinite(seconds) ? Math.round(seconds * 20.0D) : -1L;
    }

    private long millisecondsToTicks(String rawMilliseconds) {
        double milliseconds = Double.parseDouble(rawMilliseconds.trim());
        return Double.isFinite(milliseconds) ? Math.round(milliseconds / 50.0D) : -1L;
    }

    private float readFloat(JsonObject object, float fallback, String... keys) {
        return (float) readDouble(object, fallback, keys);
    }

    private double readDouble(JsonObject object, double fallback, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull()) {
                return element.getAsDouble();
            }
        }
        return fallback;
    }

    private Color readColor(JsonObject object) {
        String raw = readString(object, "background", "backgroundColor", "background-color", "textBackground", "text-background");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ColorUtil.parseColor(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private TextDisplay.TextAlignment readAlignment(JsonObject object) {
        String raw = readString(object, "alignment", "textAlignment", "text-alignment");
        if (raw == null || raw.isBlank()) {
            return TextDisplay.TextAlignment.CENTER;
        }
        try {
            return TextDisplay.TextAlignment.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    private record ParsedGholoLocation(String worldName, Location location) {
    }

    private record GholoRow(int position, String content, JsonObject offset, JsonObject data) {
    }
}
