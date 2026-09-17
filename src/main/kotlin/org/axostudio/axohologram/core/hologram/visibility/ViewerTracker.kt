package org.axostudio.axohologram.core.hologram.visibility

import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ViewerTracker {

    private val hologramViewers = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val playerHolograms = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun addViewer(hologramId: String, viewerId: UUID): Boolean {
        val viewers = hologramViewers.computeIfAbsent(hologramId) { ConcurrentHashMap.newKeySet() }
        val added = viewers.add(viewerId)
        if (added) {
            playerHolograms.computeIfAbsent(viewerId) { ConcurrentHashMap.newKeySet() }.add(hologramId)
        }
        return added
    }

    fun removeViewer(hologramId: String, viewerId: UUID): Boolean {
        val viewers = hologramViewers[hologramId]
        val removed = viewers?.remove(viewerId) ?: false
        if (removed) {
            playerHolograms[viewerId]?.remove(hologramId)
        }
        return removed
    }

    fun isViewing(hologramId: String, viewerId: UUID): Boolean {
        return hologramViewers[hologramId]?.contains(viewerId) ?: false
    }

    fun getViewers(hologramId: String): Set<UUID> {
        return hologramViewers[hologramId]?.let { Collections.unmodifiableSet(it) } ?: emptySet()
    }

    fun getViewedHolograms(viewerId: UUID): Set<String> {
        return playerHolograms[viewerId]?.let { Collections.unmodifiableSet(it) } ?: emptySet()
    }

    fun removePlayer(viewerId: UUID): Set<String> {
        val viewed = playerHolograms.remove(viewerId) ?: return emptySet()
        for (holoId in viewed) {
            hologramViewers[holoId]?.remove(viewerId)
        }
        return viewed
    }

    fun removeHologram(hologramId: String): Set<UUID> {
        val viewers = hologramViewers.remove(hologramId) ?: return emptySet()
        for (viewerId in viewers) {
            playerHolograms[viewerId]?.remove(hologramId)
        }
        return viewers
    }

    fun clear() {
        hologramViewers.clear()
        playerHolograms.clear()
    }
}
