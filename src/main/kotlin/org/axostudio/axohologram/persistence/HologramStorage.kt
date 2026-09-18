package org.axostudio.axohologram.persistence

import org.axostudio.axohologram.api.hologram.Hologram

interface HologramStorage {
    fun loadAll(): Collection<Hologram>
    fun loadAllWithReport(): HologramLoadReport = HologramLoadReport(loadAll().toList())
    fun load(id: String): Hologram?
    fun save(hologram: Hologram)
    fun saveAll(holograms: Collection<Hologram>)
    fun delete(id: String): Boolean
}
