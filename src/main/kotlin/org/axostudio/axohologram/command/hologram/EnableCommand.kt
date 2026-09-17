package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.bukkit.command.CommandSender

class EnableCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager
) : SubCommand {

    override val name: String = "enable"
    override val permission: String = "axohologram.enable"
    override val aliases: List<String> = listOf("activar")
    override val description: String = "Enables a hologram"
    override val usage: String = "/holo enable <id>"

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("enable-usage", "<red>Usage: $usage</red>")))
            return
        }

        val id = args[0]
        val hologram = hologramService.getHologram(id)
        if (hologram == null) {
            val msg = configManager.getMessage("hologram-not-found", "<red>Hologram <hologram_id> not found!</red>")
                .replace("<hologram_id>", id)
                .replace("%name%", id)
            sender.sendMessage(MiniMessageUtil.parse(msg))
            return
        }

        if (hologram.isEnabled) {
            val msg = configManager.getMessage("enable-already-enabled", "<yellow>Hologram '<hologram_id>' is already enabled.</yellow>")
                .replace("<hologram_id>", id)
                .replace("%name%", id)
            sender.sendMessage(MiniMessageUtil.parse(msg))
            return
        }

        hologramService.enable(hologram)

        val successMsg = configManager.getMessage("enable-success", "<green>Hologram '<hologram_id>' has been enabled.</green>")
            .replace("<hologram_id>", id)
            .replace("%name%", id)
        sender.sendMessage(MiniMessageUtil.parse(successMsg))
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            val input = args[0].lowercase()
            val disabled = hologramService.allHolograms
                .filter { !it.isEnabled && it.id.lowercase().startsWith(input) }
                .map { it.id }
            if (disabled.isNotEmpty()) return disabled
            return hologramService.allHologramIds.filter { it.lowercase().startsWith(input) }
        }
        return emptyList()
    }
}
