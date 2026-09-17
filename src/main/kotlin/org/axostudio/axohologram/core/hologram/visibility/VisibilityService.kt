package org.axostudio.axohologram.core.hologram.visibility

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.config.VisibilityConfig
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VisibilityService(
    val tracker: ViewerTracker = ViewerTracker(),
    var config: VisibilityConfig = VisibilityConfig()
) {
    private val manualViewers = ConcurrentHashMap<String, MutableSet<UUID>>()

    fun updatePlayerVisibility(player: Player, holograms: Collection<Hologram>, onShow: (Player, Hologram) -> Unit, onHide: (Player, Hologram) -> Unit) {
        val viewerId = player.uniqueId
        for (hologram in holograms) {
            val canSee = canPlayerSee(player, hologram)
            val isCurrentlyViewing = tracker.isViewing(hologram.id, viewerId)

            if (canSee && !isCurrentlyViewing) {
                if (tracker.addViewer(hologram.id, viewerId)) {
                    onShow(player, hologram)
                }
            } else if (!canSee && isCurrentlyViewing) {
                if (tracker.removeViewer(hologram.id, viewerId)) {
                    onHide(player, hologram)
                }
            }
        }
    }

    fun canPlayerSee(player: Player, hologram: Hologram): Boolean {
        return when (hologram.visibilityMode) {
            VisibilityMode.MANUAL -> hologram.isEnabled && manualViewers[hologram.id.lowercase()]?.contains(player.uniqueId) == true
            VisibilityMode.CONDITION, VisibilityMode.SCRIPT -> false
            else -> VisibilityRule.canPlayerSee(player, hologram)
        }
    }

    fun showManually(player: Player, hologram: Hologram) {
        manualViewers.computeIfAbsent(hologram.id.lowercase()) { ConcurrentHashMap.newKeySet() }.add(player.uniqueId)
    }

    fun hideManually(player: Player, hologram: Hologram) {
        manualViewers[hologram.id.lowercase()]?.remove(player.uniqueId)
    }

    fun handlePlayerQuit(viewerId: UUID, onHideHologram: (UUID, String) -> Unit) {
        manualViewers.values.forEach { it.remove(viewerId) }
        clearTrackedViewers(viewerId, onHideHologram)
    }

    /**
     * Clears only the render state. Manual visibility membership is deliberately
     * kept, so manually shown holograms reappear when the player returns.
     */
    fun clearTrackedViewers(viewerId: UUID, onHideHologram: (UUID, String) -> Unit) {
        for (holoId in tracker.removePlayer(viewerId)) onHideHologram(viewerId, holoId)
    }

    fun handleHologramDelete(hologramId: String, onHideFromViewer: (UUID, String) -> Unit) {
        manualViewers.remove(hologramId.lowercase())
        val viewers = tracker.removeHologram(hologramId)
        for (viewerId in viewers) {
            onHideFromViewer(viewerId, hologramId)
        }
    }
}
