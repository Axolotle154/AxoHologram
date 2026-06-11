package org.axostudio.axohologram.media;

import org.bukkit.configuration.ConfigurationSection;

public record MediaSettings(
        double width,
        double height,
        double scale,
        int renderDistance,
        int maxResolution,
        MediaQuality quality,
        boolean lookAtPlayer,
        float rotation,
        int fps,
        boolean loop,
        boolean autoplay
) {

    private static final int MAP_SIZE = 128;

    public MediaSettings {
        width = normalizePositive(width, 1.0D);
        height = normalizePositive(height, 1.0D);
        scale = normalizePositive(scale, 1.0D);
        maxResolution = autoResolution(maxResolution, width, height, scale);
        renderDistance = Math.max(1, renderDistance);
        quality = quality == null ? MediaQuality.MEDIUM : quality;
        fps = clamp(fps, 1, 240);
    }

    public static MediaSettings defaults(MediaType type, ConfigurationSection mediaConfig) {
        ConfigurationSection root = mediaConfig == null ? null : mediaConfig.getConfigurationSection("media-system");
        ConfigurationSection performance = root == null ? null : root.getConfigurationSection("performance");
        int renderDistance = performance == null ? 32 : performance.getInt("render-distance", 32);

        if (type == MediaType.VIDEO) {
            ConfigurationSection videos = root == null ? null : root.getConfigurationSection("videos");
            double width = 6.0D;
            double height = 4.0D;
            double scale = 1.0D;
            int maxResolution = autoResolution(videos == null ? 256 : videos.getInt("max-resolution", 256), width, height, scale);
            int defaultFps = videos == null ? 15 : videos.getInt("default-fps", 15);
            int maxFps = videos == null ? 20 : videos.getInt("max-fps", 20);
            boolean autoplay = videos == null || videos.getBoolean("autoplay", true);
            return new MediaSettings(width, height, scale, renderDistance, maxResolution,
                    MediaQuality.MEDIUM, false, 0.0F, clamp(defaultFps, 1, Math.max(1, maxFps)), true, autoplay);
        }

        ConfigurationSection images = root == null ? null : root.getConfigurationSection("images");
        double width = 4.0D;
        double height = 3.0D;
        double scale = 1.0D;
        int maxResolution = autoResolution(images == null ? 512 : images.getInt("max-resolution", 512), width, height, scale);
        return new MediaSettings(width, height, scale, renderDistance, maxResolution,
                MediaQuality.HIGH, false, 0.0F, 1, false, true);
    }

    public static MediaSettings fromSection(MediaType type, ConfigurationSection section, ConfigurationSection mediaConfig) {
        MediaSettings defaults = defaults(type, mediaConfig);
        if (section == null) {
            return defaults;
        }

        int maxAllowedFps = resolveMaxAllowedFps(mediaConfig);
        double width = normalizePositive(section.getDouble("width", defaults.width()), defaults.width());
        double height = normalizePositive(section.getDouble("height", defaults.height()), defaults.height());
        double scale = normalizePositive(section.getDouble("scale", defaults.scale()), defaults.scale());
        int maxResolution = autoResolution(section.getInt("max-resolution", defaults.maxResolution()), width, height, scale);
        int renderDistance = readRenderDistance(section, defaults.renderDistance());
        return new MediaSettings(
                width,
                height,
                scale,
                renderDistance,
                maxResolution,
                MediaQuality.fromString(section.getString("quality"), defaults.quality()),
                section.getBoolean("look-at-player", defaults.lookAtPlayer()),
                (float) section.getDouble("rotation", defaults.rotation()),
                clamp(section.getInt("fps", defaults.fps()), 1, Math.max(1, maxAllowedFps)),
                section.getBoolean("loop", defaults.loop()),
                section.getBoolean("autoplay", defaults.autoplay())
        );
    }

    public MediaSettings withDimensions(double width, double height) {
        double normalizedWidth = normalizePositive(width, this.width);
        double normalizedHeight = normalizePositive(height, this.height);
        int resolvedMaxResolution = autoResolution(maxResolution, normalizedWidth, normalizedHeight, scale);
        return new MediaSettings(normalizedWidth, normalizedHeight, scale, renderDistance, resolvedMaxResolution,
                quality, lookAtPlayer, rotation, fps, loop, autoplay);
    }

    public MediaSettings withScale(double scale) {
        double normalizedScale = normalizePositive(scale, this.scale);
        int resolvedMaxResolution = autoResolution(maxResolution, width, height, normalizedScale);
        return new MediaSettings(width, height, normalizedScale, renderDistance, resolvedMaxResolution,
                quality, lookAtPlayer, rotation, fps, loop, autoplay);
    }

    public MediaSettings withRenderDistance(int renderDistance) {
        return new MediaSettings(width, height, scale, Math.max(1, renderDistance), maxResolution,
                quality, lookAtPlayer, rotation, fps, loop, autoplay);
    }

    public void serialize(ConfigurationSection section, MediaType type) {
        section.set("width", width);
        section.set("height", height);
        section.set("scale", scale);
        section.set("render-distance", renderDistance);
        section.set("max-resolution", maxResolution);
        section.set("quality", quality.name());
        section.set("look-at-player", lookAtPlayer);
        section.set("rotation", rotation);
        if (type == MediaType.VIDEO) {
            section.set("fps", fps);
            section.set("loop", loop);
            section.set("autoplay", autoplay);
        }
    }

    private static int autoResolution(int configuredResolution, double width, double height, double scale) {
        int requestedTiles = requestedTileCount(Math.max(width, height), scale);
        int requiredResolution = Math.max(MAP_SIZE, safeMapResolution(requestedTiles));
        int normalizedConfigured = roundUpToMapSize(configuredResolution);
        return Math.max(normalizedConfigured, requiredResolution);
    }

    private static int roundUpToMapSize(int resolution) {
        int normalized = Math.max(MAP_SIZE, resolution);
        return (int) Math.ceil((double) normalized / MAP_SIZE) * MAP_SIZE;
    }

    private static int requestedTileCount(double size, double scale) {
        if (!isPositiveFinite(size) || !isPositiveFinite(scale)) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(size * scale));
    }

    private static int safeMapResolution(int tiles) {
        long resolution = (long) Math.max(1, tiles) * MAP_SIZE;
        return resolution > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) resolution;
    }

    private static double normalizePositive(double value, double fallback) {
        return isPositiveFinite(value) ? value : Math.max(0.01D, fallback);
    }

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static int resolveMaxAllowedFps(ConfigurationSection mediaConfig) {
        ConfigurationSection root = mediaConfig == null ? null : mediaConfig.getConfigurationSection("media-system");
        return root == null ? 20 : root.getInt("videos.max-fps", 20);
    }

    private static int readRenderDistance(ConfigurationSection section, int defaultValue) {
        if (section == null) {
            return Math.max(1, defaultValue);
        }
        if (section.contains("visibility.distance")) {
            return Math.max(1, section.getInt("visibility.distance", defaultValue));
        }
        if (section.contains("view-distance")) {
            return Math.max(1, section.getInt("view-distance", defaultValue));
        }
        return Math.max(1, section.getInt("render-distance", defaultValue));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
