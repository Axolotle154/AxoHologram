package org.axostudio.axohologram.media;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record MediaRuntimeConfig(
        boolean systemEnabled,
        boolean imagesEnabled,
        boolean videosEnabled,
        boolean videoAutoplay,
        boolean autoPauseWithoutViewers,
        boolean unloadWhenEmpty,
        long updateIntervalTicks,
        int maxActiveVideos,
        int maxMediaPerWorld,
        int maxFrames,
        boolean lineOfSightEnabled,
        double lineOfSightMinDot,
        boolean lineOfSightBlockOcclusion,
        long visibilityCheckIntervalTicks,
        long unloadDelayTicks,
        boolean adaptiveFrameThrottle,
        int maxFrameCostPerTick,
        int maxVisualSkipTicks,
        int viewerBatchSize
) {

    public static MediaRuntimeConfig from(FileConfiguration configuration) {
        ConfigurationSection root = configuration == null ? null : configuration.getConfigurationSection("media-system");
        if (root == null) {
            root = configuration;
        }

        ConfigurationSection images = root == null ? null : root.getConfigurationSection("images");
        ConfigurationSection videos = root == null ? null : root.getConfigurationSection("videos");
        ConfigurationSection performance = root == null ? null : root.getConfigurationSection("performance");
        ConfigurationSection lineOfSight = performance == null ? null : performance.getConfigurationSection("line-of-sight");
        double viewAngleDegrees = lineOfSight == null ? 75.0D : lineOfSight.getDouble("view-angle-degrees", 75.0D);

        return new MediaRuntimeConfig(
                root == null || root.getBoolean("enabled", true),
                images == null || images.getBoolean("enabled", true),
                videos == null || videos.getBoolean("enabled", true),
                videos == null || videos.getBoolean("autoplay", true),
                videos == null || videos.getBoolean("auto-pause-without-viewers", true),
                performance == null || performance.getBoolean("unload-when-empty", true),
                Math.max(1L, performance == null ? 2L : performance.getLong("update-interval-ticks", 2L)),
                Math.max(1, performance == null ? 3 : performance.getInt("max-active-videos", 3)),
                performance == null ? 20 : performance.getInt("max-media-per-world", 20),
                Math.max(1, videos == null ? 900 : videos.getInt("max-frames", 900)),
                lineOfSight != null && lineOfSight.getBoolean("enabled", true),
                Math.cos(Math.toRadians(Math.max(1.0D, Math.min(179.0D, viewAngleDegrees)))),
                lineOfSight != null && lineOfSight.getBoolean("block-occlusion", true),
                Math.max(1L, performance == null ? 5L : performance.getLong("visibility-check-interval-ticks", 5L)),
                Math.max(0L, performance == null ? 100L : performance.getLong("unload-delay-ticks", 100L)),
                performance == null || performance.getBoolean("adaptive-frame-throttle", true),
                Math.max(1, performance == null ? 1800 : performance.getInt("max-frame-cost-per-tick", 1800)),
                Math.max(1, performance == null ? 4 : performance.getInt("max-visual-skip-ticks", 4)),
                Math.max(0, performance == null ? 6 : performance.getInt("viewer-batch-size", 6))
        );
    }
}
