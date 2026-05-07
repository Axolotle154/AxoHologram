package org.axostudio.axohologram.animation;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class AnimationManager {

    private final AxoHologram plugin;
    private final AnimationConfigManager configManager;
    private final AnimationFrameCache frameCache;
    private final AnimationRenderer renderer;
    private final AnimationTickEngine tickEngine;

    public AnimationManager(AxoHologram plugin, AnimationConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.frameCache = new AnimationFrameCache();
        this.renderer = new AnimationRenderer(configManager, frameCache);
        this.tickEngine = new AnimationTickEngine(plugin, this);
    }

    public void start() {
        tickEngine.start();
    }

    public void stop() {
        tickEngine.stop();
        frameCache.clear();
    }

    public void reloadAnimations() {
        tickEngine.stop();
        configManager.reloadAnimations();
        frameCache.clear();
        renderer.clearWarnings();
        tickEngine.start();
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().getAllHolograms().forEach(Hologram::refreshViewers);
        }
    }

    public AnimationConfigManager getConfigManager() {
        return configManager;
    }

    public String renderTextAnimations(String input, Player player) {
        return renderer.renderTextAnimations(input, player, tickEngine.currentTick());
    }

    public String restoreProtectedPlaceholders(String input) {
        return renderer.restoreProtectedPlaceholders(input);
    }

    public RenderedDisplayAnimation renderDisplayAnimation(Hologram hologram, Location location) {
        return renderer.renderDisplayAnimation(hologram, location, tickEngine.currentTick());
    }

    public boolean hasTextAnimationTags(String input) {
        return renderer.hasTextAnimationTags(input);
    }

    public boolean hasDisplayAnimation(String name) {
        return configManager.hasDisplayAnimation(name);
    }

    public String resolveDisplayAnimationName(Hologram hologram) {
        if (!hologram.isDisplayAnimationEnabled()) {
            return null;
        }

        String directAnimation = hologram.getDisplayAnimation();
        if (directAnimation != null && !directAnimation.isBlank()) {
            return directAnimation;
        }
        return configManager.getDisplayAnimationForHologram(hologram.getId()).orElse(null);
    }
}
