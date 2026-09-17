package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.menu.MenuManager
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ListCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager,
    private val menuManager: MenuManager? = null
) : SubCommand {

    override val name: String = "list"
    override val permission: String = "axohologram.list"
    override val aliases: List<String> = emptyList()
    override val usage: String = "/holo list"

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (sender is Player && menuManager != null && args.isEmpty()) {
            menuManager.openListMenu(sender)
            return
        }

        val holograms = hologramService.allHolograms
        if (holograms.isEmpty()) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("no-holograms", "<yellow>No holograms found.</yellow>")))
            return
        }

        val header = configManager.getMessage("hologram-list-header", "<gold>--- Holograms (%count%) ---</gold>")
            .replace("%count%", holograms.size.toString())
        sender.sendMessage(MiniMessageUtil.parse(header))

        for (h in holograms) {
            val loc = h.location
            val locStr = if (loc != null) "${loc.world?.name ?: "world"} (${loc.blockX}, ${loc.blockY}, ${loc.blockZ})" else "Unknown"
            sender.sendMessage(MiniMessageUtil.parse("<yellow>• <gold>${h.id}</gold> <gray>- $locStr [Pages: ${h.pageCount()}]</gray></yellow>"))
        }
    }
}
