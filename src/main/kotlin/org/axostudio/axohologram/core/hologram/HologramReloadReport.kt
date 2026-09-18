package org.axostudio.axohologram.core.hologram

import org.axostudio.axohologram.persistence.HologramLoadFailure

data class ReloadedHologramInfo(
    val id: String,
    val kind: String,
    val world: String,
    val pageCount: Int,
    val lineCount: Int,
    val warning: String? = null
)

data class ReloadedMediaInfo(
    val id: String,
    val type: String,
    val world: String
)

data class HologramReloadReport(
    val holograms: List<ReloadedHologramInfo> = emptyList(),
    val media: List<ReloadedMediaInfo> = emptyList(),
    val failures: List<HologramLoadFailure> = emptyList(),
    val skippedMediaFiles: Int = 0
) {
    val loadedCount: Int
        get() = holograms.size + media.size

    val warningCount: Int
        get() = holograms.count { it.warning != null }

    companion object {
        @JvmField
        val EMPTY = HologramReloadReport()
    }
}
