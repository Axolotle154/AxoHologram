package org.axostudio.axohologram.listener

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.core.hologram.visibility.VisibilityService
import org.axostudio.axohologram.core.render.HologramRenderer
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerWorldListener(
    private val hologramService: HologramService,
    private val visibilityService: VisibilityService,
    private val renderer: HologramRenderer,
    private val scheduler: TaskScheduler
) : Listener {

    private val lastCheckedLocations = ConcurrentHashMap<UUID, Location>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val to = event.to
        val last = lastCheckedLocations[player.uniqueId]

        if (last == null || last.world != to.world || last.distanceSquared(to) > visibilityService.config.visibilityMoveDistanceSquared) {
            lastCheckedLocations[player.uniqueId] = to.clone()
            visibilityService.updatePlayerVisibility(
                player,
                hologramService.getAll(),
                onShow = { p, h -> renderer.render(p, h) },
                onHide = { p, h -> renderer.despawn(p, h) }
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        // PlayerTeleportEvent fires before the player is moved. Checking here
        // used the old world/location and could leave the visibility tracker stale.
        refreshAfterTransition(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        // Server-side display entities are per viewer. Remove their old-world
        // state first, then recreate only holograms valid in the new world.
        visibilityService.clearTrackedViewers(player.uniqueId) { _, hologramId ->
            hologramService.getHologram(hologramId)?.let { renderer.despawn(player, it) }
        }
        refreshAfterTransition(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        visibilityService.clearTrackedViewers(player.uniqueId) { _, hologramId ->
            hologramService.getHologram(hologramId)?.let { renderer.despawn(player, it) }
        }
        refreshAfterTransition(player)
    }

    private fun refreshAfterTransition(player: org.bukkit.entity.Player) {
        scheduler.runAtEntityDelayed(player, 1L) {
            if (!player.isOnline) return@runAtEntityDelayed
            lastCheckedLocations[player.uniqueId] = player.location.clone()
            visibilityService.updatePlayerVisibility(
                player,
                hologramService.getAll(),
                onShow = { p, h -> renderer.render(p, h) },
                onHide = { p, h -> renderer.despawn(p, h) }
            )
        }
    }
}
