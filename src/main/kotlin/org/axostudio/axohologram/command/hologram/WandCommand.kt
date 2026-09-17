package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.feature.wand.HologramWandService
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class WandCommand(
    private val hologramService: HologramService,
    private val wandService: HologramWandService
) : SubCommand {

    override val name = "wand"
    override val permission = "axohologram.wand"
    override val aliases = listOf("varita")
    override val usage = "/holo wand <id>"

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage(MiniMessageUtil.parse("<red>Solo un jugador puede recibir la varita.</red>"))
            return
        }
        val id = args.firstOrNull() ?: run {
            player.sendMessage(MiniMessageUtil.parse("<red>Uso: $usage</red>"))
            return
        }
        val hologram = hologramService.getHologram(id) ?: run {
            player.sendMessage(MiniMessageUtil.parse("<red>No existe el holograma <white>$id</white>.</red>"))
            return
        }
        if (!wandService.give(player, hologram)) {
            player.sendMessage(MiniMessageUtil.parse("<red>La varita solo funciona con hologramas que tengan una línea de bloque.</red>"))
            return
        }
        player.sendMessage(MiniMessageUtil.parse("<green>Recibiste la varita para <white>${hologram.id}</white>.</green>"))
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> =
        if (args.size == 1) hologramService.allHologramIds.filter { it.startsWith(args[0], true) } else emptyList()
}
