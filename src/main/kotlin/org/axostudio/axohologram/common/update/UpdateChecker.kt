package org.axostudio.axohologram.common.update

import com.google.gson.JsonParser
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class UpdateChecker(private val plugin: Plugin) {

    companion object {
        private const val PROJECT_ID = "v7Mdthjb"
        private const val DOWNLOAD_URL = "https://modrinth.com/plugin/axohologram"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000
        private val VERSION_TOKEN_PATTERN = Pattern.compile("[A-Za-z]+|\\d+")
    }

    @Volatile
    var latestVersion: String? = null
        private set

    @Volatile
    var latestVersionUrl: String? = null
        private set

    @Volatile
    var isUpdateAvailable: Boolean = false
        private set

    fun check() {
        isUpdateAvailable = false
        latestVersion = null
        latestVersionUrl = null

        Bukkit.getAsyncScheduler().runNow(plugin) { _ ->
            var connection: HttpURLConnection? = null
            try {
                val currentVersion = plugin.pluginMeta.version
                val loader = if (Bukkit.getName().lowercase().contains("folia")) "folia" else "paper"
                val minecraftVersion = Bukkit.getMinecraftVersion()

                plugin.logger.info("Checking updates from Modrinth...")

                val url = "https://api.modrinth.com/v2/project/$PROJECT_ID/version" +
                        "?loaders=[%22$loader%22]" +
                        "&game_versions=[%22$minecraftVersion%22]" +
                        "&featured=true" +
                        "&include_changelog=false"

                connection = URI(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "${plugin.name}/$currentVersion")
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    plugin.logger.warning("Failed to check updates. Response code: $responseCode")
                    return@runNow
                }

                val versions = InputStreamReader(connection.inputStream, StandardCharsets.UTF_8).use { reader ->
                    JsonParser.parseReader(reader).asJsonArray
                }

                if (versions.isEmpty) {
                    plugin.logger.warning("No compatible featured versions found on Modrinth.")
                    return@runNow
                }

                val latest = versions[0].asJsonObject
                val versionStr = latest.get("version_number").asString
                latestVersion = versionStr
                latestVersionUrl = DOWNLOAD_URL

                val comparison = compareVersions(versionStr, currentVersion)
                if (comparison <= 0) {
                    if (comparison < 0) {
                        plugin.logger.info("You are running a newer local version: $currentVersion (Modrinth: $versionStr)")
                    } else {
                        plugin.logger.info("You are running the latest version: $currentVersion")
                    }
                    return@runNow
                }

                isUpdateAvailable = true
                plugin.logger.warning(" ")
                plugin.logger.warning("========================================")
                plugin.logger.warning("A new update is available: $versionStr (current: $currentVersion)")
                plugin.logger.warning("Download: $DOWNLOAD_URL")
                plugin.logger.warning("========================================")
                plugin.logger.warning(" ")
            } catch (exception: Exception) {
                plugin.logger.warning("Error while checking updates: ${exception.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun notifyPlayer(player: Player?) {
        if (!isUpdateAvailable || player == null || !player.isOnline) return
        if (!player.hasPermission("axohologram.update.notify")) return

        val message = Component.text()
            .append(Component.text("[AxoHologram] ", NamedTextColor.AQUA))
            .append(Component.text("A new update is available: ", NamedTextColor.YELLOW))
            .append(Component.text(latestVersion ?: "", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.text(" [Click to Download]", NamedTextColor.GOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(latestVersionUrl ?: DOWNLOAD_URL)))
            .build()

        player.sendMessage(message)
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val t1 = tokenize(v1)
        val t2 = tokenize(v2)
        val maxLen = maxOf(t1.size, t2.size)

        for (i in 0 until maxLen) {
            val s1 = t1.getOrNull(i)
            val s2 = t2.getOrNull(i)
            if (s1 == s2) continue
            if (s1 == null) return -1
            if (s2 == null) return 1

            val num1 = s1.toLongOrNull()
            val num2 = s2.toLongOrNull()

            if (num1 != null && num2 != null) {
                val cmp = num1.compareTo(num2)
                if (cmp != 0) return cmp
            } else {
                val cmp = s1.compareTo(s2, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return 0
    }

    private fun tokenize(version: String): List<String> {
        val matcher = VERSION_TOKEN_PATTERN.matcher(version)
        val tokens = mutableListOf<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }
        return tokens
    }
}
