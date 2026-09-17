package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.bukkit.command.CommandSender

class DeleteCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager
) : SubCommand {

    override val name: String = "delete"
    override val permission: String = "axohologram.delete"
    override val aliases: List<String> = listOf("remove")
    override val usage: String = "/holo delete <name>"

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("delete-usage", "<red>Usage: $usage</red>")))
            return
        }

        val id = args[0]
        if (!hologramService.exists(id)) {
            val msg = configManager.getMessage("hologram-not-found", "<red>Hologram %name% not found!</red>")
                .replace("%name%", id)
            sender.sendMessage(MiniMessageUtil.parse(msg))
            return
        }

        if (!hologramService.delete(id)) {
            sender.sendMessage(MiniMessageUtil.parse("<red>Deletion was cancelled.</red>"))
            return
        }
        val successMsg = configManager.getMessage("hologram-deleted", "<green>Hologram %name% deleted!</green>")
            .replace("%name%", id)
        sender.sendMessage(MiniMessageUtil.parse(successMsg))
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            val input = args[0].lowercase()
            return hologramService.allHologramIds.filter { it.lowercase().startsWith(input) }
        }
        return emptyList()
    }
}
