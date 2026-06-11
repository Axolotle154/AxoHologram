package org.axostudio.axohologram.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.config.DynamicRefreshRuntimeConfig;
import org.axostudio.axohologram.integration.MiniPlaceholdersIntegration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiniMessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final char SECTION_CHAR = '\u00A7';
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)(?:&|\u00A7)#([0-9a-f]{6})");
    private static final Pattern PLAIN_HEX_PATTERN = Pattern.compile("(?i)#([0-9a-f]{6})(?![0-9a-f])");
    private static final Pattern PLACEHOLDER_API_PATTERN = Pattern.compile("%[^%\\s]+%");
    private static final String[] HEAVY_PLACEHOLDER_MARKERS = {
            "%ajlb_",
            "%leaderboard_",
            "%vault_eco_",
            "%statistic_"
    };
    private static final String[] REALTIME_PLACEHOLDER_MARKERS = {
            "uptime",
            "timer",
            "countdown",
            "cooldown",
            "clock",
            "time",
            "remaining",
            "seconds"
    };
    private static final int DEFAULT_COMPONENT_CACHE_LIMIT = 4096;
    private static final String PLACEHOLDER_API_BATCH_SEPARATOR = "__AXOHOLOGRAM_PAPI_BATCH_BREAK_9F8C4D8A__";
    private static final Map<PlaceholderCacheKey, PlaceholderCacheEntry> PLACEHOLDER_API_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Component> MINI_MESSAGE_COMPONENT_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean placeholderApiActive;
    private static volatile boolean miniPlaceholdersActive;

    private record PlaceholderCacheKey(UUID playerId, String hologramId, String text) {
    }

    private record PlaceholderCacheEntry(String value, long expiresAtMillis) {
    }

    private MiniMessageUtil() {
    }

    public static Component parse(String text, Player player) {
        return parse(text, player, null);
    }

    public static Component parse(String text, Player player, String hologramId) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        String processedText = applyTextAnimations(text, player);
        processedText = applyPlaceholderApi(processedText, player, hologramId);
        return parsePreparedText(text, processedText, player);
    }

    public static Component parse(String text) {
        return parse(text, null);
    }

    public static String resolvePlaceholders(String text, Player player) {
        return resolvePlaceholders(text, player, null);
    }

    public static String resolvePlaceholders(String text, Player player, String hologramId) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String processedText = applyTextAnimations(text, player);
        processedText = applyPlaceholderApi(processedText, player, hologramId);
        processedText = restoreProtectedAnimationPlaceholders(processedText);

        if (!isMiniPlaceholdersActive()) {
            return processedText;
        }

        try {
            if (player != null) {
                return PLAIN_TEXT.serialize(MINI_MESSAGE.deserialize(processedText, player, MiniPlaceholdersIntegration.combinedAudiencePlaceholders()));
            }
            return PLAIN_TEXT.serialize(MINI_MESSAGE.deserialize(processedText, MiniPlaceholdersIntegration.globalPlaceholders()));
        } catch (RuntimeException exception) {
            return processedText;
        }
    }

    public static String prepareDynamicText(String text, Player player, String hologramId) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String processedText = applyTextAnimations(text, player);
        processedText = applyPlaceholderApi(processedText, player, hologramId);
        return restoreProtectedAnimationPlaceholders(processedText);
    }

    public static boolean hasPlaceholderApiPlaceholders(String text) {
        return isPlaceholderApiActive() && containsPlaceholderApiSyntax(text);
    }

    public static boolean hasMiniPlaceholders(String text) {
        return isMiniPlaceholdersActive() && MiniPlaceholdersIntegration.hasPlaceholderSyntax(text);
    }

    public static boolean hasDynamicPlaceholders(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        boolean hasPlaceholderApiSyntax = hasPlaceholderApiPlaceholders(text);
        boolean hasMiniPlaceholdersSyntax = hasMiniPlaceholders(text);
        return hasPlaceholderApiSyntax || hasMiniPlaceholdersSyntax;
    }

    public static boolean hasHeavyPlaceholders(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        for (String marker : HEAVY_PLACEHOLDER_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasRealtimePlaceholders(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        Matcher matcher = PLACEHOLDER_API_PATTERN.matcher(text);
        while (matcher.find()) {
            if (containsRealtimeMarker(matcher.group())) {
                return true;
            }
        }
        return hasMiniPlaceholders(text) && containsRealtimeMarker(text);
    }

    public static List<Component> parseLinesWithSharedPlaceholderApi(List<String> lines, Player player) {
        return parseLinesWithSharedPlaceholderApi(lines, player, null);
    }

    public static List<Component> parseLinesWithSharedPlaceholderApi(List<String> lines, Player player, String hologramId) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        if (lines.size() == 1) {
            return List.of(parse(lines.getFirst(), player, hologramId));
        }

        List<String> originalLines = new ArrayList<>(lines.size());
        for (String line : lines) {
            String normalized = line == null ? "" : line;
            if (normalized.contains(PLACEHOLDER_API_BATCH_SEPARATOR)) {
                return parseLinesIndividually(lines, player, hologramId);
            }
            originalLines.add(normalized);
        }

        List<String> animatedLines = new ArrayList<>(originalLines.size());
        boolean hasPlaceholderApiSyntax = false;
        for (String line : originalLines) {
            String animatedLine = applyTextAnimations(line, player);
            animatedLines.add(animatedLine);
            if (!hasPlaceholderApiSyntax && hasPlaceholderApiPlaceholders(animatedLine)) {
                hasPlaceholderApiSyntax = true;
            }
        }

        List<String> processedLines = hasPlaceholderApiSyntax
                ? applyPlaceholderApiBatch(animatedLines, player, hologramId)
                : animatedLines;

        List<Component> components = new ArrayList<>(processedLines.size());
        for (int i = 0; i < processedLines.size(); i++) {
            components.add(parsePreparedText(originalLines.get(i), processedLines.get(i), player));
        }
        return components;
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

    private static String applyPlaceholderApi(String text, Player player, String hologramId) {
        if (player == null || !isPlaceholderApiActive() || !containsPlaceholderApiSyntax(text)) {
            return text;
        }

        PlaceholderCacheKey cacheKey = createPlaceholderCacheKey(player, hologramId, text);
        if (cacheKey != null) {
            PlaceholderCacheEntry cacheEntry = PLACEHOLDER_API_CACHE.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cacheEntry != null) {
                if (cacheEntry.expiresAtMillis() > now) {
                    return cacheEntry.value();
                }
                PLACEHOLDER_API_CACHE.remove(cacheKey, cacheEntry);
            }

            try {
                String result = PlaceholderAPI.setPlaceholders(player, text);
                String resolvedText = result != null ? result : text;
                long cacheSeconds = resolvePlaceholderCacheSeconds(text);
                if (cacheSeconds > 0L && shouldCachePlaceholderApiResult(text, resolvedText)) {
                    PLACEHOLDER_API_CACHE.put(cacheKey, new PlaceholderCacheEntry(resolvedText, now + cacheSeconds * 1000L));
                }
                return resolvedText;
            } catch (RuntimeException | NoClassDefFoundError exception) {
                return text;
            }
        }

        try {
            String result = PlaceholderAPI.setPlaceholders(player, text);
            return result != null ? result : text;
        } catch (RuntimeException | NoClassDefFoundError exception) {
            return text;
        }
    }

    private static List<Component> parseLinesIndividually(List<String> lines, Player player, String hologramId) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(parse(line, player, hologramId));
        }
        return components;
    }

    private static boolean shouldCachePlaceholderApiResult(String originalText, String resolvedText) {
        if (resolvedText == null) {
            return false;
        }
        if (originalText != null && originalText.equals(resolvedText) && containsPlaceholderApiSyntax(originalText)) {
            return false;
        }

        String normalized = resolvedText.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals("loading") && !normalized.contains("loading...");
    }

    private static List<String> applyPlaceholderApiBatch(List<String> lines, Player player, String hologramId) {
        String joinedText = String.join(PLACEHOLDER_API_BATCH_SEPARATOR, lines);
        String resolvedText = applyPlaceholderApi(joinedText, player, hologramId);
        String[] splitLines = resolvedText.split(Pattern.quote(PLACEHOLDER_API_BATCH_SEPARATOR), -1);
        if (splitLines.length != lines.size()) {
            List<String> individuallyResolvedLines = new ArrayList<>(lines.size());
            for (String line : lines) {
                individuallyResolvedLines.add(applyPlaceholderApi(line, player, hologramId));
            }
            return individuallyResolvedLines;
        }

        List<String> resolvedLines = new ArrayList<>(splitLines.length);
        for (String splitLine : splitLines) {
            resolvedLines.add(splitLine);
        }
        return resolvedLines;
    }

    private static boolean isPlaceholderApiActive() {
        return placeholderApiActive;
    }

    private static boolean containsPlaceholderApiSyntax(String text) {
        return text != null
                && !text.isBlank()
                && PLACEHOLDER_API_PATTERN.matcher(text).find();
    }

    private static boolean isMiniPlaceholdersActive() {
        return miniPlaceholdersActive;
    }

    public static void refreshRuntimeState() {
        AxoHologram plugin = AxoHologram.getInstance();
        if (plugin == null || plugin.getConfigManager() == null) {
            placeholderApiActive = false;
            miniPlaceholdersActive = false;
            return;
        }

        DynamicRefreshRuntimeConfig config = plugin.getConfigManager().getDynamicRefreshRuntimeConfig();
        placeholderApiActive = config.placeholderApiIntegrationEnabled()
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        miniPlaceholdersActive = config.miniPlaceholdersIntegrationEnabled()
                && Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders");
    }

    private static Component parsePreparedText(String originalText, String processedText, Player player) {
        String restoredText = restoreProtectedAnimationPlaceholders(processedText);
        String miniMessageText = convertLegacyToMiniMessage(restoredText);
        boolean miniPlaceholdersActive = isMiniPlaceholdersActive();
        boolean hasMiniPlaceholders = miniPlaceholdersActive
                && miniMessageText.indexOf('<') >= 0
                && MiniPlaceholdersIntegration.hasPlaceholderSyntax(miniMessageText);
        int componentCacheLimit = resolveComponentCacheLimit();
        boolean cacheable = canCachePreparedComponent(miniMessageText, hasMiniPlaceholders, componentCacheLimit);
        if (cacheable) {
            Component cached = MINI_MESSAGE_COMPONENT_CACHE.get(miniMessageText);
            if (cached != null) {
                return cached;
            }
        }

        try {
            Component parsed;
            if (hasMiniPlaceholders) {
                if (player != null) {
                    parsed = MINI_MESSAGE.deserialize(miniMessageText, player, MiniPlaceholdersIntegration.combinedAudiencePlaceholders());
                } else {
                    parsed = MINI_MESSAGE.deserialize(miniMessageText, MiniPlaceholdersIntegration.globalPlaceholders());
                }
            } else {
                parsed = MINI_MESSAGE.deserialize(miniMessageText);
            }
            if (cacheable) {
                cachePreparedComponent(miniMessageText, parsed, componentCacheLimit);
            }
            return parsed;
        } catch (RuntimeException exception) {
            return Component.text(originalText);
        }
    }

    private static String convertLegacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        if (!mayContainLegacyFormatting(input)) {
            return input;
        }

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

    private static boolean mayContainLegacyFormatting(String input) {
        return input.indexOf('&') >= 0 || input.indexOf(SECTION_CHAR) >= 0 || input.indexOf('#') >= 0;
    }

    private static boolean canCachePreparedComponent(String miniMessageText, boolean hasMiniPlaceholders, int cacheLimit) {
        if (miniMessageText == null || miniMessageText.isEmpty() || cacheLimit <= 0) {
            return false;
        }
        return !hasMiniPlaceholders;
    }

    private static void cachePreparedComponent(String miniMessageText, Component component, int cacheLimit) {
        if (cacheLimit <= 0) {
            return;
        }
        if (MINI_MESSAGE_COMPONENT_CACHE.size() >= cacheLimit) {
            MINI_MESSAGE_COMPONENT_CACHE.clear();
        }
        MINI_MESSAGE_COMPONENT_CACHE.put(miniMessageText, component);
    }

    private static int resolveComponentCacheLimit() {
        DynamicRefreshRuntimeConfig config = runtimeConfig();
        return config == null ? DEFAULT_COMPONENT_CACHE_LIMIT : config.miniMessageComponentCacheSize();
    }

    public static void clearPlaceholderApiCache() {
        PLACEHOLDER_API_CACHE.clear();
    }

    public static void clearPlaceholderApiCache(UUID playerId) {
        if (playerId == null || PLACEHOLDER_API_CACHE.isEmpty()) {
            return;
        }

        PLACEHOLDER_API_CACHE.keySet().removeIf(cacheKey -> playerId.equals(cacheKey.playerId()));
    }

    public static void clearPlaceholderApiCache(UUID playerId, String hologramId) {
        if (playerId == null || hologramId == null || hologramId.isBlank() || PLACEHOLDER_API_CACHE.isEmpty()) {
            return;
        }

        PLACEHOLDER_API_CACHE.keySet().removeIf(cacheKey ->
                playerId.equals(cacheKey.playerId()) && hologramId.equals(cacheKey.hologramId()));
    }

    private static PlaceholderCacheKey createPlaceholderCacheKey(Player player, String hologramId, String text) {
        if (player == null || hologramId == null || hologramId.isBlank() || text == null || text.isEmpty()) {
            return null;
        }
        if (!isPlaceholderCachingEnabled() || !hasPlaceholderApiPlaceholders(text) || hasRealtimePlaceholders(text)) {
            return null;
        }

        long cacheSeconds = resolvePlaceholderCacheSeconds(text);
        if (cacheSeconds <= 0L) {
            return null;
        }

        return new PlaceholderCacheKey(player.getUniqueId(), hologramId, text);
    }

    private static boolean isPlaceholderCachingEnabled() {
        DynamicRefreshRuntimeConfig config = runtimeConfig();
        return config != null && config.placeholderResultCachingEnabled();
    }

    private static long resolvePlaceholderCacheSeconds(String text) {
        DynamicRefreshRuntimeConfig config = runtimeConfig();
        if (config == null) {
            return 0L;
        }

        if (hasHeavyPlaceholders(text)) {
            return config.heavyPlaceholderCacheSeconds();
        }

        return config.placeholderCacheSeconds();
    }

    private static DynamicRefreshRuntimeConfig runtimeConfig() {
        AxoHologram plugin = AxoHologram.getInstance();
        return plugin == null || plugin.getConfigManager() == null
                ? null
                : plugin.getConfigManager().getDynamicRefreshRuntimeConfig();
    }

    private static boolean containsRealtimeMarker(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String marker : REALTIME_PLACEHOLDER_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String convertLegacyHex(String input) {
        String normalized = convertBungeeHex(input);
        Matcher matcher = LEGACY_HEX_PATTERN.matcher(normalized);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("<#" + matcher.group(1).toUpperCase() + ">"));
        }
        matcher.appendTail(buffer);
        return convertPlainHex(buffer.toString());
    }

    private static String convertPlainHex(String input) {
        StringBuilder output = new StringBuilder(input.length() + 16);
        int segmentStart = 0;
        int tagStart = -1;

        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '<') {
                tagStart = i;
                continue;
            }

            if (current == '>' && tagStart >= 0) {
                appendPlainHexSegment(output, input.substring(segmentStart, tagStart));
                output.append(input, tagStart, i + 1);
                segmentStart = i + 1;
                tagStart = -1;
            }
        }

        if (segmentStart < input.length()) {
            appendPlainHexSegment(output, input.substring(segmentStart));
        }

        return output.toString();
    }

    private static void appendPlainHexSegment(StringBuilder output, String segment) {
        Matcher matcher = PLAIN_HEX_PATTERN.matcher(segment);
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement("<#" + matcher.group(1).toUpperCase() + ">"));
        }
        matcher.appendTail(output);
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
