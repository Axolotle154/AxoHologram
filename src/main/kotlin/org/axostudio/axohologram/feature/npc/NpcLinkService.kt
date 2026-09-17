package org.axostudio.axohologram.feature.npc

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.compatibility.npc.NpcBridge
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.axostudio.axohologram.platform.scheduler.AxoScheduler
import org.bukkit.Location
import java.util.concurrent.ConcurrentHashMap

class NpcLinkService(
    private val hologramService: HologramService,
    private val bridges: List<NpcBridge>,
    private val scheduler: TaskScheduler,
    private val defaultYOffset: () -> Double = { 2.2 },
    private val syncInterval: () -> Long = { 10L }
) {
    private val links = ConcurrentHashMap<String, NpcLink>()
    private var syncTask: AxoScheduler.TaskHandle? = null

    fun start() {
        stop()
        val interval = syncInterval()
        if (interval <= 0L) return
        syncTask = scheduler.runTimer(interval, interval) {
            syncAll()
        }
    }

    fun stop() {
        syncTask?.cancel()
        syncTask = null
        links.clear()
    }

    fun link(hologramId: String, npcId: String, yOffset: Double = defaultYOffset()) {
        val link = NpcLink(hologramId, npcId, yOffset)
        links[hologramId.lowercase()] = link
        val holo = hologramService.get(hologramId).orElse(null)
        holo?.linkedNpc = npcId
        sync(link)
    }

    fun unlink(hologramId: String) {
        links.remove(hologramId.lowercase())
        val holo = hologramService.get(hologramId).orElse(null)
        holo?.linkedNpc = null
    }

    fun getLink(hologramId: String): NpcLink? = links[hologramId.lowercase()]

    /**
     * Returns NPC identifiers from every enabled provider. This is intentionally
     * read-only so it can be used safely by command completion.
     */
    fun getNpcIdentifiers(input: String = ""): List<String> = bridges.asSequence()
        .filter { bridge -> runCatching { bridge.isAvailable }.getOrDefault(false) }
        .flatMap { bridge -> runCatching { bridge.npcIdentifiers.asSequence() }.getOrDefault(emptySequence()) }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .filter { identifier -> identifier.startsWith(input, ignoreCase = true) }
        .sortedBy(String::lowercase)
        .toList()

    private fun syncAll() {
        for (link in links.values) {
            sync(link)
        }
    }

    private fun sync(link: NpcLink) {
        val holo = hologramService.get(link.hologramId).orElse(null) ?: return
        for (bridge in bridges) {
            if (!bridge.isAvailable) continue
            val optLoc = bridge.getLocation(link.npcIdentifier)
            if (optLoc.isPresent) {
                val npcLoc = optLoc.get()
                val targetLoc = npcLoc.clone().add(0.0, link.yOffset, 0.0)
                val currentLoc = holo.location
                if (!locationsMatch(currentLoc, targetLoc)) {
                    holo.setLocation(targetLoc, false)
                }
                break
            }
        }
    }

    private fun locationsMatch(first: Location?, second: Location): Boolean {
        if (first == null) return false
        val firstWorld = first.world
        val secondWorld = second.world
        if (firstWorld?.uid != secondWorld?.uid) return false

        return org.axostudio.axohologram.common.math.VectorUtil.distanceSquared(first, second) <= 0.0001 &&
            kotlin.math.abs(first.yaw - second.yaw) < 0.01f &&
            kotlin.math.abs(first.pitch - second.pitch) < 0.01f
    }
}
