package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.model.AxoHologram
import org.axostudio.axohologram.core.hologram.model.HologramPosition
import org.axostudio.axohologram.core.hologram.HologramManager
import org.axostudio.axohologram.common.validation.HologramId
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CloneCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager
) : SubCommand {

    override val name: String = "clone"
    override val permission: String = "axohologram.create"
    override val aliases: List<String> = listOf("copy")
    override val usage: String = "/holo clone <source> <newId>"

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("clone-usage", "<red>Usage: $usage</red>")))
            return
        }

        val sourceId = args[0]
        val newId = args[1]

        if (!HologramId.isValid(newId)) {
            sender.sendMessage(MiniMessageUtil.parse("<red>Invalid hologram id. Use 1-64 letters, numbers, underscores or hyphens.</red>"))
            return
        }

        val source = hologramService.getHologram(sourceId)
        if (source == null) {
            val msg = configManager.getMessage("hologram-not-found", "<red>Hologram %name% not found!</red>")
                .replace("%name%", sourceId)
            sender.sendMessage(MiniMessageUtil.parse(msg))
            return
        }

        if (hologramService.exists(newId)) {
            val msg = configManager.getMessage("hologram-already-exists", "<red>Hologram %name% already exists!</red>")
                .replace("%name%", newId)
            sender.sendMessage(MiniMessageUtil.parse(msg))
            return
        }

        val cloned = (source as? AxoHologram)?.cloneWithId(newId) ?: run {
            sender.sendMessage(MiniMessageUtil.parse("<red>Cannot clone hologram.</red>"))
            return
        }

        if (sender is Player) {
            cloned.position = HologramPosition.fromLocation(sender.location)
        }

        if (hologramService is HologramManager) {
            hologramService.registerCreatedHologram(cloned)
        } else {
            hologramService.registerHologram(cloned)
        }
        hologramService.saveAll()

        val msg = configManager.getMessage("hologram-cloned", "<green>Hologram %source% cloned as %target%!</green>")
            .replace("%source%", sourceId)
            .replace("%target%", newId)
        sender.sendMessage(MiniMessageUtil.parse(msg))
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            val input = args[0].lowercase()
            return hologramService.allHologramIds.filter { it.lowercase().startsWith(input) }
        }
        return emptyList()
    }
}
