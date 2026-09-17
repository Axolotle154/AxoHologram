package org.axostudio.axohologram.core.hologram.action

import org.axostudio.axohologram.api.action.HologramClickType
import org.axostudio.axohologram.api.hologram.Hologram
import org.bukkit.entity.Player

class ActionManager(private val executor: ActionExecutor) {

    fun executeActions(player: Player, hologram: Hologram, clickType: HologramClickType) {
        val specificActions = hologram.getActions(clickType)
        val anyActions = if (clickType != HologramClickType.ANY) hologram.getActions(HologramClickType.ANY) else emptyList()

        for (action in specificActions) {
            executor.execute(player, hologram, action)
        }
        for (action in anyActions) {
            executor.execute(player, hologram, action)
        }
    }
}
