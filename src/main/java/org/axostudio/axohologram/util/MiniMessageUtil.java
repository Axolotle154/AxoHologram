package org.axostudio.axohologram.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.integration.MiniPlaceholdersIntegration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiniMessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final char SECTION_CHAR = '\u00A7';
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)(?:&|\u00A7)#([0-9a-f]{6})");
    private static final Pattern PLACEHOLDER_API_PATTERN = Pattern.compile("%[^%\\s]+%");
    private static volatile Method placeholderApiMethod;
    private static volatile boolean placeholderApiLookupAttempted;

    private MiniMessageUtil() {
    }

    public static Component parse(String text, Player player) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        String processedText = applyTextAnimations(text, player);
        processedText = applyPlaceholderApi(processedText, player);
        processedText = restoreProtectedAnimationPlaceholders(processedText);
        processedText = convertLegacyToMiniMessage(processedText);

        try {
            if (isMiniPlaceholdersActive()) {
                if (player != null) {
                    return MINI_MESSAGE.deserialize(processedText, player, MiniPlaceholdersIntegration.combinedAudiencePlaceholders());
                }
                return MINI_MESSAGE.deserialize(processedText, MiniPlaceholdersIntegration.globalPlaceholders());
            }
            return MINI_MESSAGE.deserialize(processedText);
        } catch (RuntimeException exception) {
            return Component.text(text);
        }
    }

    public static Component parse(String text) {
        return parse(text, null);
    }

    public static boolean hasDynamicPlaceholders(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        boolean hasPlaceholderApiSyntax = isPlaceholderApiActive() && PLACEHOLDER_API_PATTERN.matcher(text).find();
        boolean hasMiniPlaceholdersSyntax = isMiniPlaceholdersActive() && MiniPlaceholdersIntegration.hasPlaceholderSyntax(text);
        return hasPlaceholderApiSyntax || hasMiniPlaceholdersSyntax;
    }

    public static boolean hasAnimationTags(String text) {
        AxoHologram plugin = AxoHologram.getInstance();
        return plugin != null
                && plugin.getAnimationManager() != null
                && plugin.getAnimationManager().hasTextAnimationTags(text);
    }

    private static String applyTextAnimations(String text, Player player) {
        AxoHologram plugin = AxoHologram.getInstance();
        if (plugin == null || plugin.getAnimationManager() == null) {
            return text;
        }
        return plugin.getAnimationManager().renderTextAnimations(text, player);
    }

    private static String restoreProtectedAnimationPlaceholders(String text) {
        AxoHologram plugin = AxoHologram.getInstance();
        if (plugin == null || plugin.getAnimationManager() == null) {
            return text;
        }
        return plugin.getAnimationManager().restoreProtectedPlaceholders(text);
    }

    private static String applyPlaceholderApi(String text, Player player) {
        if (player == null || !isPlaceholderApiActive()) {
            return text;
        }

        try {
            Method method = resolvePlaceholderApiMethod();
            if (method == null) {
                return text;
            }

            Object result = method.invoke(null, player, text);
            return result instanceof String stringResult ? stringResult : text;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return text;
        }
    }

    private static boolean isPlaceholderApiActive() {
        AxoHologram plugin = AxoHologram.getInstance();
        return plugin != null
                && plugin.getConfigManager() != null
                && plugin.getConfigManager().getConfig().getBoolean("integrations.placeholderapi", true)
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private static boolean isMiniPlaceholdersActive() {
        AxoHologram plugin = AxoHologram.getInstance();
        return plugin != null
                && plugin.getConfigManager() != null
                && plugin.getConfigManager().getConfig().getBoolean("integrations.miniplaceholders", true)
                && Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders");
    }

    private static Method resolvePlaceholderApiMethod() {
        if (placeholderApiMethod != null) {
            return placeholderApiMethod;
        }
        if (placeholderApiLookupAttempted) {
            return null;
        }

        synchronized (MiniMessageUtil.class) {
            if (placeholderApiMethod != null) {
                return placeholderApiMethod;
            }
            if (placeholderApiLookupAttempted) {
                return null;
            }

            placeholderApiLookupAttempted = true;
            try {
                Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                placeholderApiMethod = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            } catch (ReflectiveOperationException exception) {
                placeholderApiMethod = null;
            }
            return placeholderApiMethod;
        }
    }

    private static String convertLegacyToMiniMessage(String input) {
        String withHex = convertLegacyHex(input);
        StringBuilder output = new StringBuilder(withHex.length() + 32);
        Deque<String> activeFormats = new ArrayDeque<>();

        for (int i = 0; i < withHex.length(); i++) {
            char current = withHex.charAt(i);
            if ((current == '&' || current == SECTION_CHAR) && i + 1 < withHex.length()) {
                char code = Character.toLowerCase(withHex.charAt(i + 1));
                String tag = switch (code) {
                    case '0' -> "black";
                    case '1' -> "dark_blue";
                    case '2' -> "dark_green";
                    case '3' -> "dark_aqua";
                    case '4' -> "dark_red";
                    case '5' -> "dark_purple";
                    case '6' -> "gold";
                    case '7' -> "gray";
                    case '8' -> "dark_gray";
                    case '9' -> "blue";
                    case 'a' -> "green";
                    case 'b' -> "aqua";
                    case 'c' -> "red";
                    case 'd' -> "light_purple";
                    case 'e' -> "yellow";
                    case 'f' -> "white";
                    case 'k' -> "obfuscated";
                    case 'l' -> "bold";
                    case 'm' -> "strikethrough";
                    case 'n' -> "underlined";
                    case 'o' -> "italic";
                    case 'r' -> "reset";
                    default -> null;
                };

                if (tag != null) {
                    if (code == 'r') {
                        activeFormats.clear();
                        output.append("<reset>");
                    } else {
                        if (isFormatCode(code)) {
                            if (!activeFormats.contains(tag)) {
                                activeFormats.addLast(tag);
                                output.append('<').append(tag).append('>');
                            }
                        } else {
                            activeFormats.clear();
                            output.append('<').append(tag).append('>');
                        }
                    }
                    i++;
                    continue;
                }
            }

            output.append(current);
        }

        return output.toString();
    }

    private static String convertLegacyHex(String input) {
        String normalized = convertBungeeHex(input);
        Matcher matcher = LEGACY_HEX_PATTERN.matcher(normalized);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("<#" + matcher.group(1).toUpperCase() + ">"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String convertBungeeHex(String input) {
        StringBuilder output = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if ((current == '&' || current == SECTION_CHAR) && i + 13 < input.length()
                    && Character.toLowerCase(input.charAt(i + 1)) == 'x') {
                StringBuilder hex = new StringBuilder(6);
                boolean valid = true;
                for (int j = 0; j < 6; j++) {
                    int codeMarkerIndex = i + 2 + (j * 2);
                    int hexIndex = codeMarkerIndex + 1;
                    if (hexIndex >= input.length()) {
                        valid = false;
                        break;
                    }

                    char marker = input.charAt(codeMarkerIndex);
                    char hexChar = input.charAt(hexIndex);
                    if ((marker != '&' && marker != SECTION_CHAR) || !isHexChar(hexChar)) {
                        valid = false;
                        break;
                    }
                    hex.append(Character.toUpperCase(hexChar));
                }

                if (valid) {
                    output.append("<#").append(hex).append('>');
                    i += 13;
                    continue;
                }
            }

            output.append(current);
        }
        return output.toString();
    }

    private static boolean isFormatCode(char code) {
        return code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o';
    }

    private static boolean isHexChar(char value) {
        char normalized = Character.toLowerCase(value);
        return (normalized >= '0' && normalized <= '9') || (normalized >= 'a' && normalized <= 'f');
    }
}
