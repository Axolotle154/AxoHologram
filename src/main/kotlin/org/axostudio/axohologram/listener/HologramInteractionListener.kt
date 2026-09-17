package org.axostudio.axohologram.listener

import org.axostudio.axohologram.api.action.HologramClickType
import org.axostudio.axohologram.api.event.HologramClickEvent
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.core.hologram.action.ActionManager
import org.axostudio.axohologram.core.render.HologramRenderer
import org.axostudio.axohologram.platform.packet.HologramPacketManager
import org.bukkit.Bukkit
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent

class HologramInteractionListener(
    private val hologramService: HologramService,
    private val actionManager: ActionManager,
    private val renderer: HologramRenderer? = null
) : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        if (entity is Interaction && HologramPacketManager.isTrackedInteraction(entity.uniqueId)) {
            event.isCancelled = true
            val holoId = HologramPacketManager.getHologramIdForEntity(entity.uniqueId) ?: return
            val holo = hologramService.getHologram(holoId) ?: return
            val tracked = HologramPacketManager.getTrackedDisplay(entity.uniqueId)

            val clickEvent = HologramClickEvent(event.player, holo, HologramClickType.RIGHT, tracked?.pageIndex() ?: 0, tracked?.lineIndex() ?: 0)
            Bukkit.getPluginManager().callEvent(clickEvent)
            if (!clickEvent.isCancelled) {
                actionManager.executeActions(event.player, holo, HologramClickType.RIGHT)
                renderer?.render(event.player, holo, isUpdate = true)
            }
        }
    }

    @EventHandler
    fun onAttack(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        val damager = event.damager as? org.bukkit.entity.Player ?: return
        if (entity is Interaction && HologramPacketManager.isTrackedInteraction(entity.uniqueId)) {
            event.isCancelled = true
            val holoId = HologramPacketManager.getHologramIdForEntity(entity.uniqueId) ?: return
            val holo = hologramService.getHologram(holoId) ?: return
            val tracked = HologramPacketManager.getTrackedDisplay(entity.uniqueId)

            val clickEvent = HologramClickEvent(damager, holo, HologramClickType.LEFT, tracked?.pageIndex() ?: 0, tracked?.lineIndex() ?: 0)
            Bukkit.getPluginManager().callEvent(clickEvent)
            if (!clickEvent.isCancelled) {
                actionManager.executeActions(damager, holo, HologramClickType.LEFT)
                renderer?.render(damager, holo, isUpdate = true)
            }
        }
    }
}
