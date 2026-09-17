package org.axostudio.axohologram.config

import org.bukkit.configuration.file.FileConfiguration

data class RuntimeConfig(
    val maxTrackedEntitiesPerPlayer: Int = 500,
    val textLineLengthLimit: Int = 2048,
    val dynamicTextRefreshInterval: Long = 20L,
    val asyncLineProcessing: Boolean = true,
    val packetBatching: Boolean = true
) {
    companion object {
        fun from(config: FileConfiguration?): RuntimeConfig {
            if (config == null) return RuntimeConfig()
            return RuntimeConfig(
                maxTrackedEntitiesPerPlayer = config.getInt("performance.max-tracked-entities-per-player", 500),
                textLineLengthLimit = config.getInt("performance.text-line-length-limit", 2048),
                dynamicTextRefreshInterval = config.getLong("performance.dynamic-text-refresh-interval", 20L),
                asyncLineProcessing = config.getBoolean("performance.async-line-processing", true),
                packetBatching = config.getBoolean("performance.packet-batching", true)
            )
        }
    }
}
