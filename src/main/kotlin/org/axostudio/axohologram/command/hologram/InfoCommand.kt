package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.bukkit.command.CommandSender

class InfoCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager
) : SubCommand {

    override val name: String = "info"
    override val permission: String = "axohologram.info"
    override val aliases: List<String> = listOf("information")
    override val usage: String = "/holo info <name>"

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("info-usage", "<red>Usage: $usage</red>")))
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

        val loc = holo.location
        val locStr = if (loc != null) "${loc.world?.name ?: "world"} (${loc.blockX}, ${loc.blockY}, ${loc.blockZ})" else "Unknown"

        sender.sendMessage(MiniMessageUtil.parse("<gold>=== Hologram Info: <yellow>${holo.id}</yellow> ===</gold>"))
        sender.sendMessage(MiniMessageUtil.parse("<gray>Location:</gray> <white>$locStr</white>"))
        sender.sendMessage(MiniMessageUtil.parse("<gray>Visibility:</gray> <white>${holo.visibilityMode.name}</white> (Dist: ${holo.viewDistance})"))
        sender.sendMessage(MiniMessageUtil.parse("<gray>Billboard:</gray> <white>${holo.billboard.name}</white>"))
        sender.sendMessage(MiniMessageUtil.parse("<gray>Pages:</gray> <white>${holo.pageCount()}</white>"))
        sender.sendMessage(MiniMessageUtil.parse("<gray>Scale:</gray> <white>X:${holo.scaleX} Y:${holo.scaleY} Z:${holo.scaleZ}</white>"))
        if (holo.linkedNpc != null) {
            sender.sendMessage(MiniMessageUtil.parse("<gray>Linked NPC:</gray> <aqua>${holo.linkedNpc}</aqua>"))
        }
        if (holo.displayAnimation != null) {
            sender.sendMessage(MiniMessageUtil.parse("<gray>Animation:</gray> <aqua>${holo.displayAnimation}</aqua>"))
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
