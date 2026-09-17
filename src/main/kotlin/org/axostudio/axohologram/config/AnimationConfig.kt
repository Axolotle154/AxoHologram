package org.axostudio.axohologram.config

import org.bukkit.configuration.file.FileConfiguration

data class AnimationConfig(
    val enabled: Boolean = true,
    val tickRate: Long = 1L,
    val maxConcurrentAnimations: Int = 100,
    val cacheFrames: Boolean = true,
    val defaultInterpolationDuration: Int = 2
) {
    companion object {
        fun from(config: FileConfiguration?): AnimationConfig {
            if (config == null) return AnimationConfig()
            return AnimationConfig(
                enabled = config.getBoolean("animations.enabled", true),
                tickRate = config.getLong("animations.tick-rate", 1L).coerceAtLeast(1L),
                maxConcurrentAnimations = config.getInt("animations.max-concurrent", 100),
                cacheFrames = config.getBoolean("animations.cache-frames", true),
                defaultInterpolationDuration = config.getInt("animations.default-interpolation-duration", 2)
            )
        }
    }
}
