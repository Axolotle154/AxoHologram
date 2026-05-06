package org.axostudio.axohologram.integration;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiniPlaceholdersIntegration {

    private static final Pattern TAG_PATTERN = Pattern.compile("<(/?)([a-zA-Z0-9_#-]+)(?::[^<>]*)?>");
    private static final Set<String> BUILT_IN_TAGS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
            "obfuscated", "bold", "strikethrough", "underlined", "italic", "reset",
            "newline", "br", "hover", "click", "insertion", "keybind", "translatable", "translate",
            "fallback", "selector", "score", "nbt", "font", "gradient", "transition", "rainbow",
            "pride", "color", "shadow_color"
    );

    private MiniPlaceholdersIntegration() {
    }

    public static boolean register() {
        try {
            MiniPlaceholders.globalPlaceholders();
            MiniPlaceholders.audiencePlaceholders();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static TagResolver globalPlaceholders() {
        return MiniPlaceholders.globalPlaceholders();
    }

    public static TagResolver audiencePlaceholders() {
        return MiniPlaceholders.audiencePlaceholders();
    }

    public static TagResolver combinedAudiencePlaceholders() {
        return TagResolver.resolver(globalPlaceholders(), audiencePlaceholders());
    }

    public static boolean hasPlaceholderSyntax(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        Matcher matcher = TAG_PATTERN.matcher(text);
        while (matcher.find()) {
            if (!matcher.group(1).isEmpty()) {
                continue;
            }

            String tagName = matcher.group(2).toLowerCase(Locale.ROOT);
            if (tagName.startsWith("#")) {
                continue;
            }
            if (!BUILT_IN_TAGS.contains(tagName)) {
                return true;
            }
        }

        return false;
    }
}
