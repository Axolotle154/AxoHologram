package org.axostudio.axohologram.core.hologram.action

import org.axostudio.axohologram.api.action.HologramAction
import org.axostudio.axohologram.api.action.HologramActionType
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class ActionExecutor(
    private val plugin: Plugin,
    private val scheduler: TaskScheduler
) {
    fun execute(player: Player, hologram: Hologram?, action: HologramAction) {
        val holoId = hologram?.id ?: ""
        val resolvedValue = MiniMessageUtil.resolvePlaceholders(
            action.value.replace("{player}", player.name)
                .replace("{hologram}", holoId),
            player
        )

        when (action.type) {
            HologramActionType.COMMAND -> {
                scheduler.runAtEntity(player) {
                    player.performCommand(resolvedValue.removePrefix("/"))
                }
            }
            HologramActionType.CONSOLE_COMMAND -> {
                scheduler.runGlobal {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedValue.removePrefix("/"))
                }
            }
            HologramActionType.MESSAGE -> {
                scheduler.runAtEntity(player) {
                    player.sendMessage(MiniMessageUtil.parse(resolvedValue, player))
                }
            }
            HologramActionType.SOUND -> {
                scheduler.runAtEntity(player) {
                    try {
                        val sound = Sound.valueOf(resolvedValue.trim().uppercase())
                        player.playSound(player.location, sound, 1.0f, 1.0f)
                    } catch (ignored: Exception) {
                    }
                }
            }
            HologramActionType.PAGE -> {
                if (hologram == null) return
                when (resolvedValue.trim().lowercase()) {
                    "next", "+1" -> hologram.changePage(player, 1)
                    "previous", "prev", "back", "-1" -> hologram.changePage(player, -1)
                    else -> resolvedValue.trim().toIntOrNull()?.let { hologram.setCurrentPage(player, it - 1) }
                }
            }
            else -> {
            }
        }
    }
}
