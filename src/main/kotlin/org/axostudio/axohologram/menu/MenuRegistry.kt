package org.axostudio.axohologram.menu

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MenuRegistry {

    private val openMenus = ConcurrentHashMap<UUID, (InventoryClickEvent) -> Unit>()

    fun registerOpenMenu(player: Player, onClick: (InventoryClickEvent) -> Unit) {
        openMenus[player.uniqueId] = onClick
    }

    fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val handler = openMenus[player.uniqueId] ?: return
        event.isCancelled = true
        handler(event)
    }

    fun handleClose(player: Player) {
        openMenus.remove(player.uniqueId)
    }
}
