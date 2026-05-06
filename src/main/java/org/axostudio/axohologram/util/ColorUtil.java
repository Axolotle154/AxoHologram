package org.axostudio.axohologram.util;

import org.bukkit.Color;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public final class ColorUtil {

    private static final Color TRANSPARENT = Color.fromARGB(0, 0, 0, 0);

    private static final List<String> COMMON_COLOR_SUGGESTIONS = List.of(
            "red",
            "blue",
            "yellow",
            "green",
            "black",
            "white",
            "gray",
            "grey",
            "orange",
            "purple",
            "pink",
            "brown",
            "transparent",
            "transparente"
    );

    private ColorUtil() {
    }

    public static Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Color cannot be empty.");
        }

        String normalized = raw.trim();
        if (isTransparentKeyword(normalized)) {
            return TRANSPARENT;
        }

        if (normalized.contains(",")) {
            return parseRgb(normalized);
        }

        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }

        if (normalized.matches("[0-9a-fA-F]{8}")) {
            return Color.fromARGB((int) Long.parseLong(normalized, 16));
        }

        if (normalized.matches("[0-9a-fA-F]{6}")) {
            int rgb = Integer.parseInt(normalized, 16);
            return Color.fromRGB(rgb);
        }

        return switch (normalizeColorName(normalized)) {
            case "white", "blanco" -> Color.WHITE;
            case "silver", "lightgray", "lightgrey", "grisclaro" -> Color.SILVER;
            case "gray", "grey", "gris" -> Color.GRAY;
            case "darkgray", "darkgrey", "grisoscuro" -> Color.fromRGB(85, 85, 85);
            case "black", "negro" -> Color.BLACK;
            case "red", "rojo" -> Color.RED;
            case "maroon", "darkred", "rojooscuro" -> Color.MAROON;
            case "yellow", "amarillo" -> Color.YELLOW;
            case "gold", "dorado", "oro" -> Color.fromRGB(255, 170, 0);
            case "olive", "oliva" -> Color.OLIVE;
            case "lime", "lightgreen", "verdeclaro", "limon" -> Color.LIME;
            case "green", "verde" -> Color.GREEN;
            case "darkgreen", "verdeoscuro" -> Color.fromRGB(0, 100, 0);
            case "aqua", "cyan", "cian", "celeste", "turquesa" -> Color.AQUA;
            case "teal", "verdeagua" -> Color.TEAL;
            case "blue", "azul" -> Color.BLUE;
            case "lightblue", "azulclaro", "cielo" -> Color.fromRGB(85, 170, 255);
            case "navy", "darkblue", "azuloscuro" -> Color.NAVY;
            case "fuchsia", "magenta", "pink", "rosa", "fucsia" -> Color.FUCHSIA;
            case "purple", "morado", "purpura", "violeta" -> Color.PURPLE;
            case "orange", "naranja" -> Color.ORANGE;
            case "brown", "cafe", "marron" -> Color.fromRGB(139, 69, 19);
            case "beige", "crema" -> Color.fromRGB(245, 245, 220);
            default -> throw new IllegalArgumentException("Unsupported color: " + raw);
        };
    }

    public static Color parseNullableColor(String raw) {
        return parseColor(raw);
    }

    public static List<String> commonColorSuggestions() {
        return COMMON_COLOR_SUGGESTIONS;
    }

    public static boolean isTransparentKeyword(String raw) {
        if (raw == null) {
            return false;
        }

        String normalized = normalizeColorName(raw);
        return normalized.equals("transparent")
                || normalized.equals("transparente")
                || normalized.equals("none")
                || normalized.equals("null")
                || normalized.equals("clear");
    }

    public static String toHex(Color color) {
        if (color == null) {
            return "null";
        }

        if (color.getAlpha() != 255) {
            return String.format("#%08X", color.asARGB());
        }

        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String toDisplayName(Color color) {
        if (color == null || color.getAlpha() == 0) {
            return "transparent";
        }
        return toHex(color);
    }

    private static Color parseRgb(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid RGB color: " + raw);
        }

        int red = parseChannel(parts[0]);
        int green = parseChannel(parts[1]);
        int blue = parseChannel(parts[2]);
        return Color.fromRGB(red, green, blue);
    }

    private static int parseChannel(String raw) {
        int value = Integer.parseInt(raw.trim());
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Color channel out of range: " + raw);
        }
        return value;
    }

    private static String normalizeColorName(String raw) {
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return normalized.replace("_", "");
    }
}
