package org.axostudio.axohologram.importer;

import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.visibility.VisibilityMode;
import org.axostudio.axohologram.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ImportParserUtil {

    private ImportParserUtil() {
    }

    record ParsedLocation(String worldName, Location location) {
    }

    static ParsedLocation readLocation(ConfigurationSection section) {
        ConfigurationSection locationSection = section.getConfigurationSection("location");
        ConfigurationSection source = locationSection == null ? section : locationSection;
        String worldName = readString(source, "world", "world_name", "world-name");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        Location location = new Location(
                Bukkit.getWorld(worldName),
                readDouble(source, 0.0D, "x"),
                readDouble(source, 0.0D, "y"),
                readDouble(source, 0.0D, "z"),
                (float) readDouble(source, 0.0D, "yaw"),
                (float) readDouble(source, 0.0D, "pitch")
        );
        return new ParsedLocation(worldName, location);
    }

    static VisibilityMode readVisibility(ConfigurationSection section) {
        String raw = readString(section, "visibility", "visibility.mode", "visibility_mode");
        return VisibilityMode.fromString(raw);
    }

    static Billboard readBillboard(ConfigurationSection section) {
        return Billboard.fromString(readString(section, "billboard"));
    }

    static TextDisplay.TextAlignment readAlignment(ConfigurationSection section) {
        String raw = readString(section, "text_alignment", "text-alignment", "style.alignment", "alignment");
        if (raw == null || raw.isBlank()) {
            return TextDisplay.TextAlignment.CENTER;
        }
        try {
            return TextDisplay.TextAlignment.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    static Color readColor(ConfigurationSection section) {
        String raw = readString(section, "background", "style.background", "background_color", "text_background");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ColorUtil.parseColor(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static Vector readTranslation(ConfigurationSection section) {
        return new Vector(
                readDouble(section, 0.0D, "translation_x", "translation.x", "offset.x"),
                readDouble(section, 0.0D, "translation_y", "translation.y", "offset.y"),
                readDouble(section, 0.0D, "translation_z", "translation.z", "offset.z")
        );
    }

    static Vector readOffset(ConfigurationSection section) {
        ConfigurationSection offsetSection = section.getConfigurationSection("offset");
        if (offsetSection != null) {
            return new Vector(
                    offsetSection.getDouble("x", 0.0D),
                    offsetSection.getDouble("y", 0.0D),
                    offsetSection.getDouble("z", 0.0D)
            );
        }
        return new Vector(
                readDouble(section, 0.0D, "offset_x", "offset.x"),
                readDouble(section, 0.0D, "offset_y", "offset.y"),
                readDouble(section, 0.0D, "offset_z", "offset.z")
        );
    }

    static ItemStack readItemStack(ConfigurationSection section) {
        ItemStack direct = section.getItemStack("item");
        if (direct != null) {
            return direct.clone();
        }
        Object raw = section.get("item");
        if (raw instanceof ItemStack itemStack) {
            return itemStack.clone();
        }
        if (raw instanceof Map<?, ?> map) {
            return readItemStackMap(map);
        }
        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection != null) {
            return readItemStackMap(itemSection.getValues(false));
        }
        String material = readString(section, "material", "item", "content", "id");
        return material == null ? null : new ItemStack(readItemMaterial(material));
    }

    static ItemStack readItemStackMap(Map<?, ?> rawMap) {
        try {
            Map<String, Object> serialized = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> serialized.put(String.valueOf(key), value));
            return ItemStack.deserialize(serialized);
        } catch (RuntimeException ignored) {
            Object id = firstPresent(rawMap, "id", "type", "material");
            Material material = readItemMaterial(id == null ? "PAPER" : String.valueOf(id));
            int amount = Math.max(1, (int) readDouble(firstPresent(rawMap, "count", "amount"), 1.0D));
            return new ItemStack(material, amount);
        }
    }

    static BlockData readBlockData(ConfigurationSection section) {
        String raw = readString(section, "block", "block_state", "block-state", "block_data", "block-data", "material", "content");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return readBlockData(raw);
    }

    static BlockData readBlockData(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = normalizeNamespaced(raw);
        try {
            return Bukkit.createBlockData(normalized);
        } catch (IllegalArgumentException ignored) {
            return Bukkit.createBlockData(normalized.toLowerCase(Locale.ROOT));
        }
    }

    static SourceLine parseDecentLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        String normalized = line.toLowerCase(Locale.ROOT);
        for (String prefix : List.of("#icon:", "icon:", "#item:", "item:", "[item]:", "[icon]:", "[item]", "[icon]")) {
            if (normalized.startsWith(prefix)) {
                String material = line.substring(prefix.length()).trim();
                if (isPlayerHeadWithIdentifier(material)) {
                    return new SourceLine(LineType.ITEM, material, null, null, new Vector(), null, null, null);
                }
                ItemStack itemStack = new ItemStack(readItemMaterial(material));
                return new SourceLine(LineType.ITEM, itemStack.getType().name(), itemStack, null, new Vector(), null, null, null);
            }
        }
        for (String prefix : List.of("#block:", "block:", "[block]:", "[block]")) {
            if (normalized.startsWith(prefix)) {
                String block = line.substring(prefix.length()).trim();
                try {
                    BlockData blockData = readBlockData(block);
                    return new SourceLine(LineType.BLOCK, blockData.getAsString(), null, blockData, new Vector(), null, null, null);
                } catch (IllegalArgumentException ignored) {
                    return new SourceLine(LineType.TEXT, rawLine == null ? "" : rawLine, null, null, new Vector(), null, null, null);
                }
            }
        }
        return new SourceLine(LineType.TEXT, rawLine == null ? "" : rawLine, null, null, new Vector(), null, null, null);
    }

    static String readString(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                String value = section.getString(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    static boolean readBoolean(ConfigurationSection section, boolean fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getBoolean(key, fallback);
            }
        }
        return fallback;
    }

    static int readInt(ConfigurationSection section, int fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getInt(key, fallback);
            }
        }
        return fallback;
    }

    static long readLong(ConfigurationSection section, long fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getLong(key, fallback);
            }
        }
        return fallback;
    }

    static float readFloat(ConfigurationSection section, float fallback, String... keys) {
        return (float) readDouble(section, fallback, keys);
    }

    static double readDouble(ConfigurationSection section, double fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getDouble(key, fallback);
            }
        }
        return fallback;
    }

    static double readDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static Material readItemMaterial(String raw) {
        String normalized = normalizeNamespaced(raw);
        Material material = Material.matchMaterial(normalized);
        if (material == null) {
            material = Material.matchMaterial(normalized.toUpperCase(Locale.ROOT));
        }
        if (material == null || material.isAir() || !material.isItem()) {
            return Material.PAPER;
        }
        return material;
    }

    static String normalizeNamespaced(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim();
        return normalized.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())
                ? normalized.substring("minecraft:".length())
                : normalized;
    }

    static boolean isPlayerHeadWithIdentifier(String raw) {
        if (raw == null) {
            return false;
        }

        String normalized = normalizeNamespaced(stripItemPrefix(raw)).toLowerCase(Locale.ROOT);
        return normalized.startsWith("player_head(") && normalized.endsWith(")");
    }

    static String stripItemPrefix(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        for (String prefix : List.of("#item:", "item:", "#icon:", "icon:", "[item]:", "[icon]:", "[item]", "[icon]")) {
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed;
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }
}
