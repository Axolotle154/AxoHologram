package org.axostudio.axohologram.core.render

import org.axostudio.axohologram.api.hologram.Hologram
import org.bukkit.Location
import org.bukkit.entity.Player

data class RenderContext(
    val player: Player,
    val hologram: Hologram,
    val pageIndex: Int,
    val baseLocation: Location
)
