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
        val legacyMessagesFile = File(plugin.dataFolder, "messages.yml")
        val englishLanguageFile = findLanguageFile("en_US") ?: File(langFolder, "en_US.yml")
        val spanishLanguageFile = findLanguageFile("es_ES") ?: File(langFolder, "es_ES.yml")
        val hadLanguageFiles = hasLanguageFiles()

        saveDefaultIfMissing("lang/en_US.yml", englishLanguageFile)
        if (legacyMessagesFile.exists() && !hadLanguageFiles) {
            // Preserve the old Spanish/custom messages as the Spanish file,
            // then provide the bundled English file for the new format.
            runCatching { legacyMessagesFile.copyTo(spanishLanguageFile, overwrite = false) }
        } else {
            saveDefaultIfMissing("lang/es_ES.yml", spanishLanguageFile)
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

        val langFile = findLanguageFile(pluginConfig.language)
            ?: File(langFolder, "${pluginConfig.language}.yml")
        val fallbackLangFile = findLanguageFile("en_US")
            ?: File(langFolder, "en_US.yml")

        messages = if (langFile.exists()) {
            loadYaml(langFile, "lang/en_US.yml")
        } else if (fallbackLangFile.exists()) {
            loadYaml(fallbackLangFile, "lang/en_US.yml")
        } else {
            // Check legacy messages.yml
            val legacyMessages = File(plugin.dataFolder, "messages.yml")
            if (legacyMessages.exists()) loadYaml(legacyMessages, "lang/en_US.yml") else YamlConfiguration()
        }

        // Parsed messages are cached, so a language/prefix edit made before
        // /holo reload must invalidate the old components immediately.
        MiniMessageUtil.clearCache()
    }

    fun getMessage(key: String, def: String = key): String {
        val message = messages.getString(key) ?: def
        return if (key.equals("prefix", ignoreCase = true)) {
            message
        } else {
            withPrefix(message)
        }
    }

    fun withPrefix(message: String): String =
        MessageTemplateResolver.resolve(message, messages.getString("prefix").orEmpty())

    private fun saveDefaultIfMissing(resourcePath: String, destination: File) {
        if (!destination.exists()) {
            try {
                destination.parentFile?.mkdirs()
                plugin.saveResource(resourcePath, false)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun hasLanguageFiles(): Boolean = langFolder.listFiles()
        ?.any { it.isFile && it.extension.equals("yml", ignoreCase = true) }
        ?: false

    private fun findLanguageFile(language: String): File? {
        val expectedName = "${language.substringBeforeLast(".yml", missingDelimiterValue = language)}.yml"
        return langFolder.listFiles()
            ?.firstOrNull { it.isFile && it.name.equals(expectedName, ignoreCase = true) }
    }

    private fun loadYaml(file: File, resourceName: String): FileConfiguration {
        val configuration = YamlConfiguration.loadConfiguration(file)
        plugin.getResource(resourceName)?.use { stream ->
            configuration.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream, StandardCharsets.UTF_8)))
        }
        return configuration
    }
}
