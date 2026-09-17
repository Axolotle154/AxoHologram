package org.axostudio.axohologram.config

import org.bukkit.configuration.file.FileConfiguration

data class PluginConfig(
    val language: String = "en_us",
    val checkUpdates: Boolean = true,
    val defaultViewDistance: Int = 48,
    val defaultLineHeight: Double = 0.28,
    val foliaOptimizations: Boolean = true,
    val autoSaveIntervalMinutes: Int = 5,
    val backupOnStart: Boolean = true,
    val backupMaxRetained: Int = 10,
    val defaultStorageType: String = "yaml"
) {
    companion object {
        fun from(config: FileConfiguration?): PluginConfig {
            if (config == null) return PluginConfig()
            return PluginConfig(
                language = config.getString("general.language", "en_us") ?: "en_us",
                checkUpdates = config.getBoolean("general.check-updates", true),
                defaultViewDistance = config.getInt("general.view-distance", 48),
                defaultLineHeight = config.getDouble(
                    "general.defaults.line-spacing",
                    config.getDouble("general.default-line-height", 0.25)
                ),
                foliaOptimizations = config.getBoolean("performance.folia-optimizations", true),
                autoSaveIntervalMinutes = config.getInt("storage.auto-save-interval", 5),
                backupOnStart = config.getBoolean("storage.backup.on-start", true),
                backupMaxRetained = config.getInt("storage.backup.max-retained", 10),
                defaultStorageType = config.getString("storage.type", "yaml") ?: "yaml"
            )
        }
    }
}
