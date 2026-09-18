package org.axostudio.axohologram.core.hologram

import org.axostudio.axohologram.api.event.HologramCreateEvent
import org.axostudio.axohologram.api.event.HologramDeleteEvent
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.validation.HologramId
import org.axostudio.axohologram.core.hologram.page.PageController
import org.axostudio.axohologram.core.hologram.model.AxoHologram
import org.axostudio.axohologram.core.hologram.visibility.VisibilityService
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.Optional

class HologramManager(
    val repository: HologramRepository = HologramRepository(),
    val factory: HologramFactory,
    val visibilityService: VisibilityService,
    val pageController: PageController = PageController()
) : HologramService {

    var onSaveRequested: (() -> Unit)? = null
    var onReloadRequested: (() -> HologramReloadReport)? = null
    var onHologramCreated: ((Hologram) -> Unit)? = null
    var onHologramDeleted: ((Hologram) -> Unit)? = null
    var onHologramUpdated: ((Hologram) -> Unit)? = null
    var onHologramSpawn: ((Player, Hologram) -> Unit)? = null
    var onHologramDespawn: ((Player, Hologram) -> Unit)? = null
    var onHologramActionsRequested: ((Player, Hologram, org.axostudio.axohologram.api.action.HologramClickType) -> Unit)? = null

    @Volatile
    var lastReloadReport: HologramReloadReport = HologramReloadReport.EMPTY
        private set

    override fun get(id: String): Optional<Hologram> {
        return Optional.ofNullable(repository.get(id))
    }

    override fun require(id: String): Hologram {
        return repository.get(id) ?: throw IllegalArgumentException("Hologram not found: $id")
    }

    override fun getHologram(id: String): Hologram? {
        return repository.get(id)
    }

    override fun getAll(): Collection<Hologram> = repository.getAll()

    override fun getAllHolograms(): Collection<Hologram> = repository.getAll()

    override fun getAllHologramIds(): Set<String> = repository.getAllIds()

    override fun exists(id: String): Boolean = repository.contains(id)

    override fun create(id: String, location: Location, lines: List<String>): Hologram {
        return create(id, location, lines, true)
    }

    override fun create(id: String, location: Location, lines: List<String>, persistent: Boolean): Hologram {
        HologramId.requireValid(id)
        require(!repository.contains(id)) { "Hologram already exists: $id" }
        val hologram = factory.createHologram(id, location, lines)
        hologram.isPersistent = persistent
        val event = HologramCreateEvent(hologram)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) {
            throw IllegalStateException("Hologram creation was cancelled: $id")
        }

        registerHologram(hologram)
        onHologramCreated?.invoke(hologram)
        return hologram
    }

    override fun createItem(id: String, location: Location, itemContent: String, persistent: Boolean): Hologram {
        return create(id, location, listOf("item:$itemContent"), persistent)
    }

    override fun createBlock(id: String, location: Location, blockContent: String, persistent: Boolean): Hologram {
        return create(id, location, listOf("block:$blockContent"), persistent)
    }

    override fun createTemporary(location: Location, lines: List<String>, durationTicks: Long): Hologram {
        val tempId = "temp_${System.currentTimeMillis()}_${(0..999).random()}"
        val holo = create(tempId, location, lines, false)
        if (durationTicks > 0) {
            Bukkit.getGlobalRegionScheduler().runDelayed(
                Bukkit.getPluginManager().getPlugin("AxoHologram") ?: return holo,
                { _ -> delete(tempId) },
                durationTicks
            )
        }
        return holo
    }

    override fun createTemporary(id: String, location: Location, lines: List<String>, durationTicks: Long): Hologram {
        val holo = create(id, location, lines, false)
        if (durationTicks > 0) {
            Bukkit.getGlobalRegionScheduler().runDelayed(
                Bukkit.getPluginManager().getPlugin("AxoHologram") ?: return holo,
                { _ -> delete(id) },
                durationTicks
            )
        }
        return holo
    }

    override fun createHologram(id: String, location: Location): Hologram {
        return create(id, location, emptyList())
    }

    override fun createHologram(id: String, location: Location, lines: List<String>): Hologram {
        return create(id, location, lines)
    }

    override fun registerHologram(hologram: Hologram) {
        HologramId.requireValid(hologram.id)
        repository.put(hologram)
        bindRuntimeCallbacks(hologram)
    }

    fun registerCreatedHologram(hologram: Hologram) {
        registerHologram(hologram)
        onHologramCreated?.invoke(hologram)
    }

    override fun delete(id: String): Boolean {
        val hologram = repository.get(id) ?: return false
        val event = HologramDeleteEvent(hologram)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false
        repository.remove(id) ?: return false

        pageController.clearHologram(id)
        visibilityService.handleHologramDelete(id) { _, _ -> }
        onHologramDeleted?.invoke(hologram)
        return true
    }

    override fun delete(hologram: Hologram): Boolean {
        return delete(hologram.id)
    }

    override fun deleteHologram(id: String) {
        delete(id)
    }

    override fun deleteHologram(hologram: Hologram) {
        delete(hologram.id)
    }

    override fun deleteAll() {
        val ids = ArrayList(repository.getAllIds())
        for (id in ids) {
            delete(id)
        }
    }

    override fun hide(hologram: Hologram) {
        hologram.isEnabled = false
        if (hologram.isPersistent) onSaveRequested?.invoke()
        onHologramUpdated?.invoke(hologram)
    }

    override fun show(hologram: Hologram) {
        hologram.isEnabled = true
        if (hologram.isPersistent) onSaveRequested?.invoke()
        onHologramUpdated?.invoke(hologram)
    }

    override fun update(hologram: Hologram) {
        onHologramUpdated?.invoke(hologram)
    }

    override fun reload() {
        lastReloadReport = onReloadRequested?.invoke() ?: HologramReloadReport.EMPTY
    }

    fun reloadWithReport(): HologramReloadReport {
        reload()
        return lastReloadReport
    }

    override fun saveAll() {
        onSaveRequested?.invoke()
    }

    private fun bindRuntimeCallbacks(hologram: Hologram) {
        val concrete = hologram as? AxoHologram ?: return
        concrete.onLocationChanged = { persist ->
            if (persist && hologram.isPersistent) onSaveRequested?.invoke()
            onHologramUpdated?.invoke(hologram)
        }
        concrete.onViewerShow = { player ->
            if (hologram.visibilityMode == org.axostudio.axohologram.core.hologram.visibility.VisibilityMode.MANUAL) {
                visibilityService.showManually(player, hologram)
            }
            updatePlayerVisibility(player, hologram, false)
        }
        concrete.onViewerHide = { player ->
            visibilityService.hideManually(player, hologram)
            if (visibilityService.tracker.removeViewer(hologram.id, player.uniqueId)) {
                onHologramDespawn?.invoke(player, hologram)
            }
        }
        concrete.onViewerUpdate = { player, force -> updatePlayerVisibility(player, hologram, force) }
        concrete.onDestroyRequested = { delete(hologram.id) }
        concrete.onRefreshRequested = { onHologramUpdated?.invoke(hologram) }
        concrete.onCanView = { player -> visibilityService.canPlayerSee(player, hologram) }
        concrete.onIsViewing = { player -> visibilityService.tracker.isViewing(hologram.id, player.uniqueId) }
        concrete.onActionsRequested = { player, click -> onHologramActionsRequested?.invoke(player, hologram, click) }
    }

    private fun updatePlayerVisibility(player: Player, hologram: Hologram, force: Boolean) {
        visibilityService.updatePlayerVisibility(
            player,
            listOf(hologram),
            onShow = { p, h -> onHologramSpawn?.invoke(p, h) },
            onHide = { p, h -> onHologramDespawn?.invoke(p, h) }
        )
        if (force && visibilityService.tracker.isViewing(hologram.id, player.uniqueId)) {
            onHologramSpawn?.invoke(player, hologram)
        }
    }
}
