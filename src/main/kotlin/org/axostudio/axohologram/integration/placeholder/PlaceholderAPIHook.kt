package org.axostudio.axohologram.integration.placeholder

import me.clip.placeholderapi.PlaceholderAPI
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.compatibility.placeholder.PlaceholderBridge
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.function.BiFunction

class PlaceholderAPIHook(
    private val plugin: Plugin,
    private val hologramService: HologramService
) : PlaceholderExpansion(), PlaceholderBridge {

    override fun getIdentifier(): String = "axohologram"
    override fun getAuthor(): String = "AxoStudio"
    override fun getVersion(): String = plugin.pluginMeta.version
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (params.equals("total", ignoreCase = true)) {
            return hologramService.getAll().size.toString()
        }
        return null
    }

    override fun isPlaceholderApiAvailable(): Boolean =
        Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")

    override fun isMiniPlaceholdersAvailable(): Boolean =
        Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")

    override fun setPlaceholders(player: Player?, text: String): String {
        if (!isPlaceholderApiAvailable() || player == null) return text
        return try {
            PlaceholderAPI.setPlaceholders(player, text)
        } catch (e: Exception) {
            text
        }
    }

    override fun setPlaceholders(player: OfflinePlayer?, text: String): String {
        if (!isPlaceholderApiAvailable() || player == null) return text
        return try {
            PlaceholderAPI.setPlaceholders(player, text)
        } catch (e: Exception) {
            text
        }
    }

    override fun registerExpansion(identifier: String, handler: BiFunction<OfflinePlayer, String, String>) {
        // Can register dynamic expansions if needed
    }

    override fun unregisterExpansion(identifier: String) {
    }
}
