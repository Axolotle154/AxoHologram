package org.axostudio.axohologram.core.render

import org.bukkit.Location
import java.util.UUID

data class RenderState(
    val entityId: UUID? = null,
    val lastLocation: Location? = null,
    val lastContent: String? = null,
    val lastRenderMillis: Long = System.currentTimeMillis()
)
