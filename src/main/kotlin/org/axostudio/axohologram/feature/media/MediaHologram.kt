package org.axostudio.axohologram.feature.media

import org.bukkit.Location
import org.bukkit.map.MapView
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class MediaType {
    IMAGE,
    VIDEO,
    GIF;

    companion object {
        /** Names accepted by 3.x commands and common file extensions. */
        fun fromInput(raw: String?): MediaType? = when (raw?.trim()?.lowercase()) {
            "image", "imagen", "png", "jpg", "jpeg", "webp" -> IMAGE
            "gif" -> GIF
            "video", "mp4", "webm" -> VIDEO
            else -> null
        }
    }
}

data class MediaHologram(
    val id: String,
    val type: MediaType,
    val source: String,
    var location: Location,
    var settings: MediaSettings = MediaSettings(),
    var isPlaying: Boolean = false,
    var currentFrameIndex: Int = 0,
    @Transient var frames: List<MapFrameData> = emptyList(),
    @Transient val mapViews: MutableList<MapView> = mutableListOf(),
    @Transient val displayIds: MutableList<UUID> = mutableListOf(),
    /** Players currently allowed to see the server-side display entities. */
    @Transient val viewerIds: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
    @Transient var nextFrameTick: Long = 0L
)
