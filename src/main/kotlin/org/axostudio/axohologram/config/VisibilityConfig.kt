package org.axostudio.axohologram.config

import org.bukkit.configuration.file.FileConfiguration

data class VisibilityConfig(
    val movementCheckMode: String = "DISTANCE",
    val visibilityMoveDistanceSquared: Double = 2.25,
    val movementCooldownTicks: Long = 5L,
    val visibilityRefreshIntervalTicks: Long = 100L,
    val periodicVisibilityCheckEnabled: Boolean = true,
    val defaultViewDistance: Int = 48
) {
    companion object {
        fun from(config: FileConfiguration?): VisibilityConfig {
            if (config == null) return VisibilityConfig()
            val moveDist = config.getDouble("performance.visibility-move-distance-blocks", 1.5).coerceAtLeast(0.0)
            return VisibilityConfig(
                movementCheckMode = config.getString("performance.movement-check-mode", "DISTANCE") ?: "DISTANCE",
                visibilityMoveDistanceSquared = moveDist * moveDist,
                movementCooldownTicks = config.getLong("performance.movement-visibility-cooldown-ticks", 5L).coerceAtLeast(0L),
                visibilityRefreshIntervalTicks = config.getLong("performance.visibility-refresh-interval-ticks", 100L).coerceAtLeast(1L),
                periodicVisibilityCheckEnabled = config.getBoolean("visibility.periodic-check-enabled", true),
                defaultViewDistance = config.getInt("general.view-distance", 48)
            )
        }
    }
}
