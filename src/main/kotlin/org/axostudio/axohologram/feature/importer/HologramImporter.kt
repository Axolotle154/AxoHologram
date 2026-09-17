package org.axostudio.axohologram.feature.importer

interface HologramImporter {
    fun id(): String
    fun displayName(): String
    fun isAvailable(): Boolean
    fun availableHolograms(): Collection<String>
    fun importHologram(name: String): ImportResult
    fun importAll(): ImportResult
    fun importAllWithoutBackup(): ImportResult = importAll()
}
