package org.axostudio.axohologram.core.hologram.page

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PageController {

    private data class PageKey(val viewerId: UUID, val hologramId: String)

    private val playerPages = ConcurrentHashMap<PageKey, Int>()

    fun getPage(viewerId: UUID, hologramId: String): Int {
        return playerPages.getOrDefault(PageKey(viewerId, hologramId), 0)
    }

    fun setPage(viewerId: UUID, hologramId: String, pageIndex: Int) {
        playerPages[PageKey(viewerId, hologramId)] = pageIndex.coerceAtLeast(0)
    }

    fun nextPage(viewerId: UUID, hologramId: String, totalPages: Int): Int {
        if (totalPages <= 1) return 0
        val current = getPage(viewerId, hologramId)
        val next = (current + 1) % totalPages
        setPage(viewerId, hologramId, next)
        return next
    }

    fun previousPage(viewerId: UUID, hologramId: String, totalPages: Int): Int {
        if (totalPages <= 1) return 0
        val current = getPage(viewerId, hologramId)
        val prev = if (current - 1 < 0) totalPages - 1 else current - 1
        setPage(viewerId, hologramId, prev)
        return prev
    }

    fun clear(viewerId: UUID) {
        playerPages.keys.removeIf { it.viewerId == viewerId }
    }

    fun clearHologram(hologramId: String) {
        playerPages.keys.removeIf { it.hologramId == hologramId }
    }
}
