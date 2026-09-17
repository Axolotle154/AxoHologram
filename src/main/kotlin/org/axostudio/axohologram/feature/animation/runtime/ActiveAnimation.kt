package org.axostudio.axohologram.feature.animation.runtime

import org.axostudio.axohologram.feature.animation.display.DisplayAnimation
import org.bukkit.Location
import org.bukkit.entity.Display
import java.lang.ref.WeakReference

data class ActiveAnimation(
    val hologramId: String,
    val animation: DisplayAnimation,
    val displayRef: WeakReference<Display>,
    val baseLocation: Location,
    var currentTick: Long = 0L
)
