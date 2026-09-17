package org.axostudio.axohologram.core.hologram.action

import org.axostudio.axohologram.api.action.HologramAction
import org.axostudio.axohologram.api.action.HologramActionType
import org.axostudio.axohologram.api.action.HologramClickType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ActionRegistry {

    private val actionMap = ConcurrentHashMap<HologramClickType, MutableList<HologramAction>>()

    fun getActions(clickType: HologramClickType): List<HologramAction> {
        return actionMap[clickType]?.toList() ?: emptyList()
    }

    fun addAction(clickType: HologramClickType, action: HologramAction) {
        actionMap.computeIfAbsent(clickType) { CopyOnWriteArrayList() }.add(action)
    }

    fun removeAction(clickType: HologramClickType, index: Int): HologramAction? {
        val list = actionMap[clickType] ?: return null
        if (index in 0 until list.size) {
            return list.removeAt(index)
        }
        return null
    }

    fun clear() {
        actionMap.clear()
    }

    fun clone(): ActionRegistry {
        val copy = ActionRegistry()
        for ((k, v) in actionMap) {
            for (action in v) {
                copy.addAction(k, action)
            }
        }
        return copy
    }
}
