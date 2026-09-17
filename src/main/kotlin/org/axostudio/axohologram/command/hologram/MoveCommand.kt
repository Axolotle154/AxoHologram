package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MoveCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager
) : SubCommand {

    override val name: String = "move"
    override val permission: String = "axohologram.hologram.move"
    override val aliases: List<String> = listOf("movehere")
    override val usage: String = "/holo move <name> [x y z]"

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("move-usage", "<red>Usage: $usage</red>")))
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

        val targetLoc: Location = if (args.size >= 4) {
            val x = args[1].toDoubleOrNull()
            val y = args[2].toDoubleOrNull()
            val z = args[3].toDoubleOrNull()
            val world = (sender as? Player)?.world ?: holo.location?.world
            if (x == null || y == null || z == null || world == null) {
                sender.sendMessage(MiniMessageUtil.parse("<red>Invalid coordinates.</red>"))
                return
            }
            Location(world, x, y, z)
        } else if (sender is Player) {
            sender.location.clone()
        } else {
            sender.sendMessage(MiniMessageUtil.parse("<red>Coordinates required for console.</red>"))
            return
        }

        holo.setLocation(targetLoc, true)
        hologramService.saveAll()

        val msg = configManager.getMessage("hologram-moved", "<green>Hologram %name% moved to your location!</green>")
            .replace("%name%", id)
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
