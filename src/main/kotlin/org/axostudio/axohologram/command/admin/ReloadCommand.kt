package org.axostudio.axohologram.command.admin

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.bukkit.command.CommandSender

class ReloadCommand(
    private val configManager: ConfigManager,
    private val hologramService: HologramService
) : SubCommand {

    override val name: String = "reload"
    override val permission: String = "axohologram.reload"
    override val aliases: List<String> = listOf("rl")
    override val usage: String = "/holo reload"

    override fun execute(sender: CommandSender, args: Array<String>) {
        configManager.reload()
        hologramService.reload()
        val msg = configManager.getMessage("reload-success", "<green>Configuration and holograms reloaded!</green>")
        sender.sendMessage(MiniMessageUtil.parse(msg))
    }
}
