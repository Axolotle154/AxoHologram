package org.axostudio.axohologram.listener

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.update.UpdateChecker
import org.axostudio.axohologram.core.hologram.visibility.VisibilityService
import org.axostudio.axohologram.core.render.HologramRenderer
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.axostudio.axohologram.menu.MenuRegistry
import org.axostudio.axohologram.platform.packet.HologramPacketManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerConnectionListener(
    private val hologramService: HologramService,
    private val visibilityService: VisibilityService,
    private val renderer: HologramRenderer,
    private val scheduler: TaskScheduler,
    private val updateChecker: UpdateChecker,
    private val menuRegistry: MenuRegistry
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        scheduler.runAtEntityDelayed(player, 5L) {
            if (player.isOnline) {
                visibilityService.updatePlayerVisibility(
                    player,
                    hologramService.getAll(),
                    onShow = { p, h -> renderer.render(p, h) },
                    onHide = { p, h -> renderer.despawn(p, h) }
                )
                updateChecker.notifyPlayer(player)
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        menuRegistry.handleClose(player)
        visibilityService.handlePlayerQuit(player.uniqueId) { viewerId, hologramId ->
            HologramPacketManager.destroyHologram(viewerId, hologramId)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        menuRegistry.handleClick(event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? org.bukkit.entity.Player ?: return
        menuRegistry.handleClose(player)
    }
}
