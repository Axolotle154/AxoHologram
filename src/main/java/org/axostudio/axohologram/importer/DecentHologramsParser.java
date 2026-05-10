package org.axostudio.axohologram.importer;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.LineType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class DecentHologramsParser {

    private static final Pattern PLACEHOLDER_API_PATTERN = Pattern.compile("%[^%\\s]+%");

    private final AxoHologram plugin;
    private final File sourceFolder;

    public DecentHologramsParser(AxoHologram plugin) {
        this.plugin = plugin;
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        this.sourceFolder = new File(pluginsFolder == null ? new File("plugins") : pluginsFolder, "DecentHolograms/holograms");
    }

    public boolean isAvailable() {
        return sourceFolder.isDirectory();
    }

    public Map<String, SourceHologram> parseAll() {
        Map<String, SourceHologram> holograms = new LinkedHashMap<>();
        File[] files = sourceFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            return holograms;
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : sortedFiles) {
            try {
                SourceHologram hologram = parseFile(file);
                holograms.put(hologram.name(), hologram);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "[IMPORT] Skipping corrupt DecentHolograms file \"" + file.getName() + "\".", exception);
            }
        }
        return holograms;
    }

    public SourceHologram parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        File exactFile = new File(sourceFolder, name + ".yml");
        if (exactFile.isFile()) {
            return parseFileSafely(exactFile);
        }

        File[] files = sourceFolder.listFiles((dir, fileName) -> fileName.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                if (stripExtension(file.getName()).equalsIgnoreCase(name)) {
                    return parseFileSafely(file);
                }
            }
        }

        for (SourceHologram hologram : parseAll().values()) {
            if (hologram.name().equalsIgnoreCase(name)) {
                return hologram;
            }
        }
        return null;
    }

    private SourceHologram parseFileSafely(File file) {
        try {
            return parseFile(file);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[IMPORT] Skipping corrupt DecentHolograms file \"" + file.getName() + "\".", exception);
            return null;
        }
    }

    private SourceHologram parseFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String name = config.getString("name", stripExtension(file.getName()));
        ImportParserUtil.ParsedLocation parsedLocation = readLocation(config);
        if (parsedLocation == null) {
            throw new IllegalArgumentException("missing location");
        }

        List<List<SourceLine>> pages = readPages(config);
        if (pages.isEmpty()) {
            pages = List.of(List.of());
        }

        String displayAnimation = plugin.getConfigManager().getConfig().getBoolean("importer.import-animations", true)
                ? ImportParserUtil.readString(config, "display-animation", "display_animation", "animation.display", "animation")
                : null;

        return new SourceHologram(
                "DecentHolograms",
                name,
                parsedLocation.worldName(),
                parsedLocation.location(),
                ImportParserUtil.readBoolean(config, true, "enabled"),
                ImportParserUtil.readInt(config, -1, "visibility-distance", "visibility_distance", "display-range", "display_range", "view-distance", "view_distance", "range"),
                ImportParserUtil.readVisibility(config),
                ImportParserUtil.readBillboard(config),
                ImportParserUtil.readFloat(config, 1.0F, "scale_x", "scale.x", "scale"),
                ImportParserUtil.readFloat(config, 1.0F, "scale_y", "scale.y", "scale"),
                ImportParserUtil.readFloat(config, 1.0F, "scale_z", "scale.z", "scale"),
                ImportParserUtil.readTranslation(config),
                ImportParserUtil.readFloat(config, 0.0F, "shadow_radius", "shadow.radius"),
                ImportParserUtil.readFloat(config, 1.0F, "shadow_strength", "shadow.strength"),
                ImportParserUtil.readColor(config),
                ImportParserUtil.readBoolean(config, false, "text_shadow", "style.text-shadow"),
                ImportParserUtil.readBoolean(config, false, "see_through", "style.see-through"),
                ImportParserUtil.readAlignment(config),
                ImportParserUtil.readLong(config, -1L, "update-interval", "update_interval", "text.update-interval", "update-text-interval"),
                displayAnimation,
                pages
        );
    }

    private ImportParserUtil.ParsedLocation readLocation(ConfigurationSection section) {
        ImportParserUtil.ParsedLocation directLocation = ImportParserUtil.readLocation(section);
        if (directLocation != null) {
            return directLocation;
        }

        for (String key : List.of("location", "pos", "position")) {
            Object raw = section.get(key);
            if (raw instanceof String locationString) {
                ImportParserUtil.ParsedLocation parsed = parseLocationString(locationString);
                if (parsed != null) {
                    return parsed;
                }
            }
            if (raw instanceof Map<?, ?> map) {
                ConfigurationSection nested = new YamlConfiguration();
                map.forEach((mapKey, value) -> nested.set(String.valueOf(mapKey), value));
                ImportParserUtil.ParsedLocation parsed = ImportParserUtil.readLocation(nested);
                if (parsed != null) {
                    return parsed;
                }
            }
            ConfigurationSection nested = section.getConfigurationSection(key);
            if (nested != null) {
                ImportParserUtil.ParsedLocation parsed = ImportParserUtil.readLocation(nested);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private ImportParserUtil.ParsedLocation parseLocationString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().replace(';', ',').replace('|', ',');
        String[] parts = normalized.contains(",") ? normalized.split(",") : normalized.split(":");
        if (parts.length < 4) {
            return null;
        }

        String worldName = parts[0].trim();
        try {
            Location location = new Location(
                    Bukkit.getWorld(worldName),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()),
                    parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0.0F,
                    parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0.0F
            );
            return new ImportParserUtil.ParsedLocation(worldName, location);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<List<SourceLine>> readPages(ConfigurationSection section) {
        List<List<SourceLine>> pages = new ArrayList<>();
        Object rawPages = section.get("pages");
        if (rawPages instanceof List<?> pageList) {
            for (Object rawPage : pageList) {
                List<SourceLine> page = readPageObject(rawPage);
                if (!page.isEmpty()) {
                    pages.add(page);
                }
            }
        }

        ConfigurationSection pagesSection = section.getConfigurationSection("pages");
        if (pagesSection != null) {
            for (String key : sortedKeys(pagesSection)) {
                ConfigurationSection pageSection = pagesSection.getConfigurationSection(key);
                if (pageSection == null) {
                    continue;
                }
                List<SourceLine> page = readLines(pageSection);
                if (!page.isEmpty()) {
                    pages.add(page);
                }
            }
        }

        if (pages.isEmpty()) {
            List<SourceLine> directLines = readLines(section);
            if (!directLines.isEmpty()) {
                pages.add(directLines);
            }
        }
        return List.copyOf(pages);
    }

    private List<SourceLine> readPageObject(Object rawPage) {
        if (rawPage instanceof Map<?, ?> map) {
            ConfigurationSection section = new YamlConfiguration();
            map.forEach((key, value) -> section.set(String.valueOf(key), value));
            return readLines(section);
        }
        if (rawPage instanceof List<?> list) {
            return readLineList(list);
        }
        if (rawPage instanceof String line) {
            return List.of(applyTextImportSettings(ImportParserUtil.parseDecentLine(line)));
        }
        return List.of();
    }

    private List<SourceLine> readLines(ConfigurationSection section) {
        for (String key : List.of("lines", "text", "content")) {
            Object rawLines = section.get(key);
            if (rawLines instanceof List<?> list) {
                return readLineList(list);
            }
            if (rawLines instanceof String line) {
                return List.of(ImportParserUtil.parseDecentLine(line));
            }

            ConfigurationSection linesSection = section.getConfigurationSection(key);
            if (linesSection != null) {
                List<SourceLine> lines = new ArrayList<>();
                for (String lineKey : sortedKeys(linesSection)) {
                    Object rawLine = linesSection.get(lineKey);
                    SourceLine line = readLineObject(rawLine);
                    if (line != null) {
                        lines.add(line);
                    }
                }
                return List.copyOf(lines);
            }
        }
        return List.of();
    }

    private List<SourceLine> readLineList(List<?> list) {
        List<SourceLine> lines = new ArrayList<>(list.size());
        for (Object rawLine : list) {
            SourceLine line = readLineObject(rawLine);
            if (line != null) {
                lines.add(applyTextImportSettings(line));
            }
        }
        return List.copyOf(lines);
    }

    private SourceLine readLineObject(Object rawLine) {
        try {
            if (rawLine instanceof String text) {
                return applyTextImportSettings(ImportParserUtil.parseDecentLine(text));
            }
            if (rawLine instanceof Map<?, ?> map) {
                ConfigurationSection lineSection = new YamlConfiguration();
                map.forEach((key, value) -> lineSection.set(String.valueOf(key), value));
                return readLineSection(lineSection);
            }
            if (rawLine instanceof ConfigurationSection lineSection) {
                return readLineSection(lineSection);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[IMPORT] Skipping corrupt DecentHolograms line.", exception);
        }
        return null;
    }

    private SourceLine readLineSection(ConfigurationSection section) {
        String content = ImportParserUtil.readString(section, "content", "text", "line", "value", "custom-name", "custom_name", "material", "id", "block");
        SourceLine parsedPrefix = content == null ? null : ImportParserUtil.parseDecentLine(content);
        LineType type = parseLineType(ImportParserUtil.readString(section, "type", "line-type", "line_type"), parsedPrefix);
        Vector offset = ImportParserUtil.readOffset(section);
        Double height = readOptionalHeight(section);
        Billboard billboard = section.contains("billboard") ? ImportParserUtil.readBillboard(section) : null;
        String permission = ImportParserUtil.readString(section, "permission", "view-permission", "view_permission");

        if (type == LineType.ITEM) {
            ItemStack itemStack = parsedPrefix != null && parsedPrefix.itemStack() != null
                    ? parsedPrefix.itemStack()
                    : ImportParserUtil.readItemStack(section);
            if (itemStack == null) {
                return null;
            }
            return new SourceLine(LineType.ITEM, itemStack.getType().name(), itemStack, null, offset, height, billboard, permission);
        }

        if (type == LineType.BLOCK) {
            BlockData blockData = parsedPrefix != null && parsedPrefix.blockData() != null
                    ? parsedPrefix.blockData()
                    : ImportParserUtil.readBlockData(section);
            if (blockData == null) {
                return null;
            }
            return new SourceLine(LineType.BLOCK, blockData.getAsString(), null, blockData, offset, height, billboard, permission);
        }

        return new SourceLine(LineType.TEXT, importText(content), null, null, offset, height, billboard, permission);
    }

    private LineType parseLineType(String rawType, SourceLine parsedPrefix) {
        if (rawType == null || rawType.isBlank()) {
            return parsedPrefix == null ? LineType.TEXT : parsedPrefix.type();
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("ITEM") || normalized.contains("ICON")) {
            return LineType.ITEM;
        }
        if (normalized.contains("BLOCK")) {
            return LineType.BLOCK;
        }
        return LineType.TEXT;
    }

    private Double readOptionalHeight(ConfigurationSection section) {
        if (section.contains("height")) {
            return Math.max(0.0D, section.getDouble("height", 0.0D));
        }
        if (section.contains("line-height")) {
            return Math.max(0.0D, section.getDouble("line-height", 0.0D));
        }
        return null;
    }

    private List<String> sortedKeys(ConfigurationSection section) {
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((first, second) -> {
            Integer firstNumber = parseInt(first);
            Integer secondNumber = parseInt(second);
            if (firstNumber != null && secondNumber != null) {
                return Integer.compare(firstNumber, secondNumber);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(first, second);
        });
        return keys;
    }

    private Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index <= 0 ? fileName : fileName.substring(0, index);
    }

    private SourceLine applyTextImportSettings(SourceLine line) {
        if (line == null || line.type() != LineType.TEXT) {
            return line;
        }
        return new SourceLine(
                LineType.TEXT,
                importText(line.content()),
                null,
                null,
                line.offset(),
                line.height(),
                line.billboard(),
                line.permission()
        );
    }

    private String importText(String text) {
        String value = text == null ? "" : text;
        if (plugin.getConfigManager().getConfig().getBoolean("importer.import-placeholders", true)) {
            return value;
        }
        return PLACEHOLDER_API_PATTERN.matcher(value).replaceAll("");
    }
}
