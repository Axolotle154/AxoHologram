package org.axostudio.axohologram.persistence

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.persistence.yaml.YamlHologramStorage
import java.io.File

class StorageManager(
    dataFolder: File,
    storageType: String = "yaml"
) {
    val storage: HologramStorage = when (storageType.lowercase()) {
        else -> YamlHologramStorage(File(dataFolder, "holograms"))
    }

    fun loadAll(): Collection<Hologram> = storage.loadAll()

    fun loadAllWithReport(): HologramLoadReport = storage.loadAllWithReport()

    fun save(hologram: Hologram) = storage.save(hologram)

    fun saveAll(holograms: Collection<Hologram>) = storage.saveAll(holograms)

    fun delete(id: String): Boolean = storage.delete(id)
}
