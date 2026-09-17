package org.axostudio.axohologram.feature.importer

import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.HologramManager
import org.axostudio.axohologram.feature.importer.decent.DecentHologramsImporter
import org.axostudio.axohologram.feature.importer.fancy.FancyHologramsImporter
import org.axostudio.axohologram.feature.importer.gholo.GholoImporter
import org.axostudio.axohologram.feature.importer.web.WebImporter
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.io.File

class ImportManager(
    hologramManager: HologramManager,
    configManager: ConfigManager,
    scheduler: TaskScheduler,
    pluginsFolder: File = File("plugins")
) {

    private val importers = ConcurrentHashMap<String, HologramImporter>()

    init {
        register(FancyHologramsImporter(hologramManager, configManager, pluginsFolder))
        register(DecentHologramsImporter(hologramManager, pluginsFolder))
        register(GholoImporter(hologramManager, configManager, pluginsFolder))
        register(WebImporter(hologramManager, configManager, scheduler))
    }

    fun register(importer: HologramImporter) {
        importers[importer.id().lowercase(Locale.ROOT)] = importer
    }

    fun getImporter(id: String): HologramImporter? = importers[id.lowercase(Locale.ROOT)]

    fun getAllImporters(): Collection<HologramImporter> = importers.values

    fun getAvailableImporters(): List<HologramImporter> = importers.values.filter { it.isAvailable() }
}
