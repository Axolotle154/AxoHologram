package org.axostudio.axohologram.animation;

import org.axostudio.axohologram.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnimationRenderer {

    public static final char PLACEHOLDER_PERCENT_MARKER = '\uE000';

    private static final Pattern TEXT_ANIMATION_PATTERN =
            Pattern.compile("(?s)<#ANIM:([a-zA-Z0-9_-]+)>(.*?)</#ANIM>");

    private final AnimationConfigManager configManager;
    private final AnimationFrameCache frameCache;
    private final Set<String> warnedMissingTextAnimations = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedMissingDisplayAnimations = ConcurrentHashMap.newKeySet();

    public AnimationRenderer(AnimationConfigManager configManager, AnimationFrameCache frameCache) {
        this.configManager = configManager;
        this.frameCache = frameCache;
    }

    public String renderTextAnimations(String input, Player player, long tick) {
        if (input == null || input.isEmpty() || !configManager.getSettings().enabled()) {
            return input;
        }

        Matcher matcher = TEXT_ANIMATION_PATTERN.matcher(input);
        StringBuffer output = new StringBuffer(input.length());
        while (matcher.find()) {
            String animationName = matcher.group(1);
            String content = matcher.group(2);
            TextAnimation animation = configManager.getTextAnimation(animationName).orElse(null);
            if (animation == null) {
                warnMissingTextAnimation(animationName);
                matcher.appendReplacement(output, Matcher.quoteReplacement(content));
                continue;
            }

            if (!configManager.getSettings().allowPlaceholdersInsideAnimations()) {
                content = content.replace('%', PLACEHOLDER_PERCENT_MARKER);
            }

            String rendered = renderTextFrame(animation, content, tick);
            matcher.appendReplacement(output, Matcher.quoteReplacement(rendered));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    public RenderedDisplayAnimation renderDisplayAnimation(Hologram hologram, Location baseLocation, long tick) {
        Location location = baseLocation.clone();
        String animationName = resolveDisplayAnimationName(hologram);
        if (!configManager.getSettings().enabled() || animationName == null) {
            return new RenderedDisplayAnimation(location, 1.0F, 0.0F, 0);
        }

        DisplayAnimation animation = configManager.getDisplayAnimation(animationName).orElse(null);
        if (animation == null) {
            warnMissingDisplayAnimation(animationName);
            return new RenderedDisplayAnimation(location, 1.0F, 0.0F, 0);
        }

        DisplayAnimationFrame frame = animation.frame(tick);
        location.add(frame.offsetX(), frame.offsetY(), frame.offsetZ());
        location.setYaw(location.getYaw() + frame.yawOffset());
        location.setPitch(location.getPitch() + frame.pitchOffset());
        return new RenderedDisplayAnimation(
                location,
                frame.scaleMultiplier(),
                frame.rollOffset(),
                configManager.getSettings().interpolationDuration()
        );
    }

    public boolean hasTextAnimationTags(String input) {
        return input != null && TEXT_ANIMATION_PATTERN.matcher(input).find();
    }

    public String restoreProtectedPlaceholders(String input) {
        return input == null ? null : input.replace(PLACEHOLDER_PERCENT_MARKER, '%');
    }

    public void clearWarnings() {
        warnedMissingTextAnimations.clear();
        warnedMissingDisplayAnimations.clear();
    }

    private String resolveDisplayAnimationName(Hologram hologram) {
        String directAnimation = hologram.getDisplayAnimation();
        if (directAnimation != null && !directAnimation.isBlank()) {
            return directAnimation;
        }
        return configManager.getDisplayAnimationForHologram(hologram.getId()).orElse(null);
    }

    private String renderTextFrame(TextAnimation animation, String content, long tick) {
        if (!configManager.getSettings().cacheFrames()) {
            return animation.render(content, tick);
        }

        String cacheKey = animation.name() + "|" + animation.frameIndex(tick) + "|" + content;
        return frameCache.getTextFrame(cacheKey, () -> animation.render(content, tick));
    }

    private void warnMissingTextAnimation(String animationName) {
        if (warnedMissingTextAnimations.add(animationName.toLowerCase())) {
            org.axostudio.axohologram.AxoHologram.getInstance().getLogger()
                    .warning("Text animation '" + animationName + "' is not registered in animations.yml.");
        }
    }

    private void warnMissingDisplayAnimation(String animationName) {
        if (warnedMissingDisplayAnimations.add(animationName.toLowerCase())) {
            org.axostudio.axohologram.AxoHologram.getInstance().getLogger()
                    .warning("Display animation '" + animationName + "' is not registered in animations.yml.");
        }
    }
}
