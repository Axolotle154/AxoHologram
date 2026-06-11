package org.axostudio.axohologram.config;

import org.bukkit.configuration.file.FileConfiguration;

public record DynamicRefreshRuntimeConfig(
        boolean staticHologramsNeverUpdate,
        boolean updateOnlyWhenTextChanges,
        boolean skipRefreshWhenNoViewers,
        long placeholderRefreshIntervalTicks,
        long dynamicRefreshIntervalTicks,
        long heavyPlaceholderRefreshIntervalTicks,
        int dynamicRefreshViewerBatchSize,
        boolean placeholderResultCachingEnabled,
        long placeholderCacheSeconds,
        long heavyPlaceholderCacheSeconds,
        int miniMessageComponentCacheSize,
        boolean placeholderApiIntegrationEnabled,
        boolean miniPlaceholdersIntegrationEnabled
) {

    public static DynamicRefreshRuntimeConfig from(FileConfiguration config) {
        long placeholderInterval = config == null
                ? 20L
                : config.getLong(
                        "placeholders.refresh-interval",
                        config.getLong("performance.dynamic-refresh-interval-ticks", 20L)
                );
        long dynamicInterval = config == null
                ? 100L
                : config.getLong(
                        "performance.dynamic-refresh-interval-ticks",
                        config.getLong(
                                "performance.dynamic-line-update-interval-ticks",
                                config.getLong("placeholders.refresh-interval", 20L)
                        )
                );
        long heavyInterval = config == null
                ? 200L
                : config.getLong("performance.heavy-placeholder-refresh-interval-ticks", placeholderInterval);

        return new DynamicRefreshRuntimeConfig(
                config == null || config.getBoolean("performance.static-holograms-never-update", true),
                config == null || config.getBoolean("performance.update-only-when-text-changes", true),
                config == null || config.getBoolean("performance.skip-refresh-when-no-viewers", true),
                placeholderInterval,
                dynamicInterval,
                heavyInterval,
                config == null ? 8 : config.getInt("performance.dynamic-refresh-viewer-batch-size", 8),
                config == null || config.getBoolean("performance.cache-placeholder-results", true),
                Math.max(0L, config == null ? 0L : config.getLong("performance.placeholder-cache-seconds", 0L)),
                Math.max(0L, config == null
                        ? 0L
                        : config.getLong(
                                "performance.heavy-placeholder-cache-seconds",
                                config.getLong("performance.placeholder-cache-seconds", 0L)
                        )),
                Math.max(0, config == null ? 4096 : config.getInt("performance.minimessage-component-cache-size", 4096)),
                config == null || config.getBoolean("integrations.placeholderapi", true),
                config == null || config.getBoolean("integrations.miniplaceholders", true)
        );
    }
}
