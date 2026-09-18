package org.axostudio.axohologram.persistence

import org.axostudio.axohologram.api.hologram.Hologram

data class HologramLoadFailure(
    val id: String,
    val source: String,
    val reason: String
)

data class HologramLoadReport(
    val holograms: List<Hologram>,
    val failures: List<HologramLoadFailure> = emptyList(),
    val skippedMediaFiles: Int = 0
)
