package org.axostudio.axohologram.common.text

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object MiniMessageUtil {

    private val MINI_MESSAGE: MiniMessage = MiniMessage.miniMessage()
    private val PLAIN_TEXT: PlainTextComponentSerializer = PlainTextComponentSerializer.plainText()
    private const val SECTION_CHAR = '\u00A7'

    private val LEGACY_HEX_PATTERN = Pattern.compile("(?i)(?:&|\u00A7)#([0-9a-f]{6})")
    private val PLAIN_HEX_PATTERN = Pattern.compile("(?i)#([0-9a-f]{6})(?![0-9a-f])")
    private val PLACEHOLDER_API_PATTERN = Pattern.compile("%[^%\\s]+%")

    private val COMPONENT_CACHE = ConcurrentHashMap<String, Component>()
    private const val MAX_CACHE_SIZE = 4096

    @Volatile
    var isPlaceholderApiActive: Boolean = false
        private set

    @Volatile
    var isMiniPlaceholdersActive: Boolean = false
        private set

    fun initHooks() {
        isPlaceholderApiActive = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        isMiniPlaceholdersActive = Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")
    }

    @JvmStatic
    fun parse(text: String?, player: Player? = null): Component {
        if (text.isNullOrEmpty()) return Component.empty()

        val processedText = resolvePlaceholders(text, player)
        val cacheKey = (if (player != null) "${player.uniqueId}:" else "") + processedText

        return COMPONENT_CACHE.computeIfAbsent(cacheKey) {
            val converted = convertLegacyToMiniMessage(processedText)
            try {
                MINI_MESSAGE.deserialize(converted)
            } catch (e: Exception) {
                Component.text(processedText)
            }
        }.also {
            if (COMPONENT_CACHE.size > MAX_CACHE_SIZE) {
                COMPONENT_CACHE.clear()
            }
        }
    }

    @JvmStatic
    fun resolvePlaceholders(text: String?, player: Player?): String {
        if (text.isNullOrEmpty()) return ""
        var result = text

        if (isPlaceholderApiActive && player != null) {
            try {
                result = PlaceholderAPI.setPlaceholders(player, result)
            } catch (ignored: Exception) {
            }
        }

        return result
    }

    /**
     * Returns true when the text contains a PlaceholderAPI token. This is
     * intentionally kept separate from resolution so the runtime can refresh
     * only holograms whose rendered value may change over time.
     */
    @JvmStatic
    fun containsPlaceholderApiToken(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        return PLACEHOLDER_API_PATTERN.matcher(text).find()
    }

    @JvmStatic
    fun convertLegacyToMiniMessage(input: String): String {
        var text = input

        // Convert hex &#RRGGBB or §#RRGGBB to <#RRGGBB>
        var matcher = LEGACY_HEX_PATTERN.matcher(text)
        val sb = StringBuilder()
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#${matcher.group(1)}>")
        }
        matcher.appendTail(sb)
        text = sb.toString()

        // Replace standard & / § color codes
        text = text
            .replace("&0", "<black>").replace("§0", "<black>")
            .replace("&1", "<dark_blue>").replace("§1", "<dark_blue>")
            .replace("&2", "<dark_green>").replace("§2", "<dark_green>")
            .replace("&3", "<dark_aqua>").replace("§3", "<dark_aqua>")
            .replace("&4", "<dark_red>").replace("§4", "<dark_red>")
            .replace("&5", "<dark_purple>").replace("§5", "<dark_purple>")
            .replace("&6", "<gold>").replace("§6", "<gold>")
            .replace("&7", "<gray>").replace("§7", "<gray>")
            .replace("&8", "<dark_gray>").replace("§8", "<dark_gray>")
            .replace("&9", "<blue>").replace("§9", "<blue>")
            .replace("&a", "<green>").replace("§a", "<green>")
            .replace("&b", "<aqua>").replace("§b", "<aqua>")
            .replace("&c", "<red>").replace("§c", "<red>")
            .replace("&d", "<light_purple>").replace("§d", "<light_purple>")
            .replace("&e", "<yellow>").replace("§e", "<yellow>")
            .replace("&f", "<white>").replace("§f", "<white>")
            .replace("&k", "<obfuscated>").replace("§k", "<obfuscated>")
            .replace("&l", "<bold>").replace("§l", "<bold>")
            .replace("&m", "<strikethrough>").replace("§m", "<strikethrough>")
            .replace("&n", "<underlined>").replace("§n", "<underlined>")
            .replace("&o", "<italic>").replace("§o", "<italic>")
            .replace("&r", "<reset>").replace("§r", "<reset>")

        return text
    }

    @JvmStatic
    fun toPlainText(component: Component): String {
        return PLAIN_TEXT.serialize(component)
    }

    @JvmStatic
    fun clearCache() {
        COMPONENT_CACHE.clear()
    }
}
