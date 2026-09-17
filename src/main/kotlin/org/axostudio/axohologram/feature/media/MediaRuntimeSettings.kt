package org.axostudio.axohologram.feature.media

import org.bukkit.configuration.file.FileConfiguration

private const val MEBIBYTE: Long = 1024L * 1024L

/**
 * Runtime limits for the media subsystem. Keeping these values in one model
 * makes media.yml authoritative instead of leaving its safeguards unused.
 */
data class MediaRuntimeSettings(
    val enabled: Boolean = true,
    val imagesEnabled: Boolean = true,
    val videosEnabled: Boolean = true,
    val imageMaxFileSizeBytes: Long = 10L * MEBIBYTE,
    val videoMaxFileSizeBytes: Long = 250L * MEBIBYTE,
    val imageMaxResolution: Int = 512,
    val videoMaxResolution: Int = 256,
    val maxVideoFps: Int = 20,
    val defaultVideoFps: Int = 15,
    val maxFrames: Int = 900,
    val videoAutoplay: Boolean = true,
    val allowPrivateAddresses: Boolean = false,
    val urlTimeoutSeconds: Int = 10,
    val renderDistance: Int = 32,
    val updateIntervalTicks: Long = 2L,
    val maxActiveVideos: Int = 3,
    val maxMediaPerWorld: Int = 20,
    val visibilityCheckIntervalTicks: Long = 5L,
    val viewerBatchSize: Int = 6
) {
    companion object {
        fun from(config: FileConfiguration?): MediaRuntimeSettings {
            if (config == null) return MediaRuntimeSettings()

            fun megabytes(path: String, fallback: Long): Long =
                config.getLong(path, fallback / MEBIBYTE).coerceAtLeast(1L) * MEBIBYTE

            val maxFps = config.getInt("media-system.videos.max-fps", 20).coerceIn(1, 120)
            return MediaRuntimeSettings(
                enabled = config.getBoolean("media-system.enabled", true),
                imagesEnabled = config.getBoolean("media-system.images.enabled", true),
                videosEnabled = config.getBoolean("media-system.videos.enabled", true),
                imageMaxFileSizeBytes = megabytes("media-system.images.max-file-size-mb", 10L * MEBIBYTE),
                videoMaxFileSizeBytes = megabytes("media-system.videos.max-file-size-mb", 250L * MEBIBYTE),
                imageMaxResolution = config.getInt("media-system.images.max-resolution", 512).coerceIn(16, 2048),
                videoMaxResolution = config.getInt("media-system.videos.max-resolution", 256).coerceIn(16, 2048),
                maxVideoFps = maxFps,
                defaultVideoFps = config.getInt("media-system.videos.default-fps", 15).coerceIn(1, maxFps),
                maxFrames = config.getInt("media-system.videos.max-frames", 900).coerceIn(1, 10_000),
                videoAutoplay = config.getBoolean("media-system.videos.autoplay", true),
                allowPrivateAddresses = config.getBoolean("media-system.urls.allow-private-addresses", false),
                urlTimeoutSeconds = config.getInt("media-system.urls.timeout-seconds", 10).coerceIn(1, 120),
                renderDistance = config.getInt("media-system.performance.render-distance", 32).coerceAtLeast(1),
                updateIntervalTicks = config.getLong("media-system.performance.update-interval-ticks", 2L).coerceAtLeast(1L),
                maxActiveVideos = config.getInt("media-system.performance.max-active-videos", 3).coerceAtLeast(1),
                maxMediaPerWorld = config.getInt("media-system.performance.max-media-per-world", 20).coerceAtLeast(1),
                visibilityCheckIntervalTicks = config.getLong("media-system.performance.visibility-check-interval-ticks", 5L).coerceAtLeast(1L),
                viewerBatchSize = config.getInt("media-system.performance.viewer-batch-size", 6).coerceAtLeast(1)
            )
        }
    }
}
