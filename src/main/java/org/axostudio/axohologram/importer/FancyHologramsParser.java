package org.axostudio.axohologram.importer;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.line.LineType;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class FancyHologramsParser {

    private static final Pattern PLACEHOLDER_API_PATTERN = Pattern.compile("%[^%\\s]+%");

    private final AxoHologram plugin;
    private final File sourceFile;

    public FancyHologramsParser(AxoHologram plugin) {
        this.plugin = plugin;
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        this.sourceFile = new File(pluginsFolder == null ? new File("plugins") : pluginsFolder, "FancyHolograms/holograms.yml");
    }

    public boolean isAvailable() {
        return sourceFile.isFile();
    }

    public Map<String, SourceHologram> parseAll() {
        Map<String, SourceHologram> holograms = new LinkedHashMap<>();
        if (!isAvailable()) {
            return holograms;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(sourceFile);
        int version = config.getInt("version", -1);
        if (version > 2) {
            plugin.getLogger().warning("[IMPORT] FancyHolograms holograms.yml version " + version + " is newer than expected. Trying to import anyway.");
        }

        ConfigurationSection root = config.getConfigurationSection("holograms");
        if (root == null) {
            plugin.getLogger().warning("[IMPORT] FancyHolograms holograms.yml has no holograms section.");
            return holograms;
        }

        for (String name : root.getKeys(false)) {
            ConfigurationSection hologramSection = root.getConfigurationSection(name);
            if (hologramSection == null) {
                plugin.getLogger().warning("[IMPORT] Skipping FancyHolograms hologram \"" + name + "\" because it is not a section.");
                continue;
            }

            try {
                SourceHologram hologram = parseHologram(name, hologramSection);
                holograms.put(hologram.name(), hologram);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "[IMPORT] Skipping corrupt FancyHolograms hologram \"" + name + "\".", exception);
            }
        }
        return holograms;
    }

    public SourceHologram parse(String name) {
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

    private SourceHologram parseHologram(String name, ConfigurationSection section) {
        ImportParserUtil.ParsedLocation parsedLocation = ImportParserUtil.readLocation(section);
        if (parsedLocation == null) {
            throw new IllegalArgumentException("missing location");
        }

        LineType type = parseType(section.getString("type", "TEXT"));
        List<List<SourceLine>> pages = switch (type) {
            case TEXT -> List.of(parseTextPage(section));
            case ITEM -> List.of(List.of(parseItemLine(section)));
            case BLOCK -> List.of(List.of(parseBlockLine(section)));
        };

        String displayAnimation = plugin.getConfigManager().getConfig().getBoolean("importer.import-animations", true)
                ? ImportParserUtil.readString(section, "display-animation", "display_animation", "animation.display", "animation")
                : null;

        return new SourceHologram(
                "FancyHolograms",
                name,
                parsedLocation.worldName(),
                parsedLocation.location(),
                section.getBoolean("enabled", true),
                ImportParserUtil.readInt(section, -1, "visibility_distance", "visibility.distance", "view-distance", "view_distance"),
                ImportParserUtil.readVisibility(section),
                ImportParserUtil.readBillboard(section),
                ImportParserUtil.readFloat(section, 1.0F, "scale_x", "scale.x"),
                ImportParserUtil.readFloat(section, 1.0F, "scale_y", "scale.y"),
                ImportParserUtil.readFloat(section, 1.0F, "scale_z", "scale.z"),
                ImportParserUtil.readTranslation(section),
                ImportParserUtil.readFloat(section, 0.0F, "shadow_radius", "shadow.radius"),
                ImportParserUtil.readFloat(section, 1.0F, "shadow_strength", "shadow.strength"),
                ImportParserUtil.readColor(section),
                ImportParserUtil.readBoolean(section, false, "text_shadow", "style.text-shadow"),
                ImportParserUtil.readBoolean(section, false, "see_through", "style.see-through"),
                ImportParserUtil.readAlignment(section),
                ImportParserUtil.readLong(section, -1L, "update_text_interval", "text.update-interval", "update-text-interval"),
                displayAnimation,
                pages
        );
    }

    private List<SourceLine> parseTextPage(ConfigurationSection section) {
        List<String> text = section.getStringList("text");
        if (text.isEmpty() && section.isList("lines")) {
            text = section.getStringList("lines");
        }
        if (text.isEmpty()) {
            String singleLine = ImportParserUtil.readString(section, "text", "content", "line");
            if (singleLine != null) {
                text = List.of(singleLine);
            }
        }
        if (text.isEmpty()) {
            text = List.of("");
        }

        List<SourceLine> lines = new ArrayList<>(text.size());
        for (String line : text) {
            lines.add(new SourceLine(LineType.TEXT, importText(line), null, null, new Vector(), null, null, null));
        }
        return List.copyOf(lines);
    }

    private SourceLine parseItemLine(ConfigurationSection section) {
        ItemStack itemStack = ImportParserUtil.readItemStack(section);
        if (itemStack == null) {
            throw new IllegalArgumentException("missing item stack");
        }
        return new SourceLine(LineType.ITEM, itemStack.getType().name(), itemStack, null, new Vector(), null, null, null);
    }

    private SourceLine parseBlockLine(ConfigurationSection section) {
        BlockData blockData = ImportParserUtil.readBlockData(section);
        if (blockData == null) {
            throw new IllegalArgumentException("missing block data");
        }
        return new SourceLine(LineType.BLOCK, blockData.getAsString(), null, blockData, new Vector(), null, null, null);
    }

    private LineType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return LineType.TEXT;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("ITEM")) {
            return LineType.ITEM;
        }
        if (normalized.contains("BLOCK")) {
            return LineType.BLOCK;
        }
        return LineType.TEXT;
    }

    private String importText(String text) {
        String value = text == null ? "" : text;
        if (plugin.getConfigManager().getConfig().getBoolean("importer.import-placeholders", true)) {
            return value;
        }
        return PLACEHOLDER_API_PATTERN.matcher(value).replaceAll("");
    }
}
