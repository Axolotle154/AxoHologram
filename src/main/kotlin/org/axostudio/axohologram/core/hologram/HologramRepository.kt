package org.axostudio.axohologram.core.hologram

import org.axostudio.axohologram.api.hologram.Hologram
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class HologramRepository {

    private val holograms = ConcurrentHashMap<String, Hologram>()

    fun get(id: String): Hologram? = holograms[id.lowercase(Locale.ROOT)]

    fun getAll(): Collection<Hologram> = Collections.unmodifiableCollection(holograms.values)

    fun getAllIds(): Set<String> = Collections.unmodifiableSet(holograms.keys)

    fun contains(id: String): Boolean = holograms.containsKey(id.lowercase(Locale.ROOT))

    fun put(hologram: Hologram) {
        holograms[hologram.id.lowercase(Locale.ROOT)] = hologram
    }

    fun remove(id: String): Hologram? = holograms.remove(id.lowercase(Locale.ROOT))

    fun clear() {
        holograms.clear()
    }

    val size: Int
        get() = holograms.size
}
