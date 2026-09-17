package org.axostudio.axohologram.config

import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class ConfigManager(private val plugin: Plugin) {

    lateinit var configFile: File
    lateinit var animationsFile: File
    lateinit var mediaFile: File
    lateinit var visibilityFile: File
    lateinit var storageFile: File
    lateinit var langFolder: File

    var config: FileConfiguration = YamlConfiguration()
        private set
    var animationsConfig: FileConfiguration = YamlConfiguration()
        private set
    var mediaConfig: FileConfiguration = YamlConfiguration()
        private set
    var visibilityConfigFile: FileConfiguration = YamlConfiguration()
        private set
    var storageConfig: FileConfiguration = YamlConfiguration()
        private set
    var messages: FileConfiguration = YamlConfiguration()
        private set

    var pluginConfig: PluginConfig = PluginConfig()
        private set
    var runtimeConfig: RuntimeConfig = RuntimeConfig()
        private set
    var visibilityConfig: VisibilityConfig = VisibilityConfig()
        private set
    var animationConfig: AnimationConfig = AnimationConfig()
        private set

    fun loadAll() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        configFile = File(plugin.dataFolder, "config.yml")
        animationsFile = File(plugin.dataFolder, "animations.yml")
        mediaFile = File(plugin.dataFolder, "media.yml")
        visibilityFile = File(plugin.dataFolder, "visibility.yml")
        storageFile = File(plugin.dataFolder, "storage.yml")
        langFolder = File(plugin.dataFolder, "lang")

        if (!langFolder.exists()) {
            langFolder.mkdirs()
        }

        saveDefaultIfMissing("config.yml", configFile)
        saveDefaultIfMissing("animations.yml", animationsFile)
        saveDefaultIfMissing("media.yml", mediaFile)
        saveDefaultIfMissing("visibility.yml", visibilityFile)
        saveDefaultIfMissing("storage.yml", storageFile)
        // 3.x installations used a single messages.yml. Do not create the new
        // language files over that migration path, otherwise the user's custom
        // legacy messages would silently stop being selected on first startup.
        val legacyMessagesFile = File(plugin.dataFolder, "messages.yml")
        if (!legacyMessagesFile.exists()) {
            saveDefaultIfMissing("lang/en_us.yml", File(langFolder, "en_us.yml"))
            saveDefaultIfMissing("lang/es_es.yml", File(langFolder, "es_es.yml"))
        }

        // A normal persisted hologram, provided once for a new installation.
        // Its marker prevents it from returning after an administrator deletes it.
        val exampleMarker = File(plugin.dataFolder, ".animation-example-installed")
        if (!exampleMarker.exists()) {
            saveDefaultIfMissing("holograms/animation_example.yml", File(plugin.dataFolder, "holograms/animation_example.yml"))
            exampleMarker.createNewFile()
        }

        reload()
    }

    fun reload() {
        config = loadYaml(configFile, "config.yml")
        animationsConfig = loadYaml(animationsFile, "animations.yml")
        mediaConfig = loadYaml(mediaFile, "media.yml")
        visibilityConfigFile = loadYaml(visibilityFile, "visibility.yml")
        storageConfig = loadYaml(storageFile, "storage.yml")

        pluginConfig = PluginConfig.from(config)
        runtimeConfig = RuntimeConfig.from(config)
        visibilityConfig = VisibilityConfig.from(if (visibilityFile.exists()) visibilityConfigFile else config)
        animationConfig = AnimationConfig.from(if (animationsFile.exists()) animationsConfig else config)

        val langName = pluginConfig.language.lowercase()
        val langFile = File(langFolder, "$langName.yml")
        val fallbackLangFile = File(langFolder, "en_us.yml")

        messages = if (langFile.exists()) {
            loadYaml(langFile, "lang/en_us.yml")
        } else if (fallbackLangFile.exists()) {
            loadYaml(fallbackLangFile, "lang/en_us.yml")
        } else {
            // Check legacy messages.yml
            val legacyMessages = File(plugin.dataFolder, "messages.yml")
            if (legacyMessages.exists()) loadYaml(legacyMessages, "lang/en_us.yml") else YamlConfiguration()
        }

        // Parsed messages are cached, so a language/prefix edit made before
        // /holo reload must invalidate the old components immediately.
        MiniMessageUtil.clearCache()
    }

    fun getMessage(key: String, def: String = key): String {
        val message = messages.getString(key) ?: def
        return MessageTemplateResolver.resolve(message, messages.getString("prefix").orEmpty())
    }

    private fun saveDefaultIfMissing(resourcePath: String, destination: File) {
        if (!destination.exists()) {
            try {
                destination.parentFile?.mkdirs()
                plugin.saveResource(resourcePath, false)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun loadYaml(file: File, resourceName: String): FileConfiguration {
        val configuration = YamlConfiguration.loadConfiguration(file)
        plugin.getResource(resourceName)?.use { stream ->
            configuration.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream, StandardCharsets.UTF_8)))
        }
        return configuration
    }
}
