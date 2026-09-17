package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.menu.MenuManager
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class EditCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager,
    private val menuManager: MenuManager? = null
) : SubCommand {

    override val name: String = "edit"
    override val permission: String = "axohologram.edit"
    override val aliases: List<String> = emptyList()
    override val usage: String = "/holo edit <name>"

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("edit-usage", "<red>Usage: $usage</red>")))
            return
        }

        val id = args[0]
        val holo = hologramService.getHologram(id)
        if (holo == null) {
            val msg = configManager.getMessage("hologram-not-found", "<red>Hologram %name% not found!</red>")
                .replace("%name%", id)
            sender.sendMessage(MiniMessageUtil.parse(msg))
            return
        }

        if (sender is Player && menuManager != null) {
            menuManager.openEditor(sender, holo)
        } else {
            sender.sendMessage(MiniMessageUtil.parse("<green>Hologram <yellow>$id</yellow> has ${holo.pages.size} pages and ${holo.pages.firstOrNull()?.lineCount() ?: 0} lines.</green>"))
        }
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            val input = args[0].lowercase()
            return hologramService.allHologramIds.filter { it.lowercase().startsWith(input) }
        }
        return emptyList()
    }
}
