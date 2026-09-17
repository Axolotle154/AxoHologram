package org.axostudio.axohologram.core.hologram

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.config.PluginConfig
import org.axostudio.axohologram.core.hologram.model.AxoHologram
import org.axostudio.axohologram.core.hologram.model.AxoHologramPage
import org.axostudio.axohologram.core.hologram.model.HologramPosition
import org.axostudio.axohologram.core.hologram.model.HologramSettings
import org.bukkit.Location

class HologramFactory(private val pluginConfig: () -> PluginConfig) {

    fun createHologram(
        id: String,
        location: Location,
        initialLines: List<String> = emptyList()
    ): Hologram {
        val config = pluginConfig()
        val settings = HologramSettings(
            viewDistance = config.defaultViewDistance,
            lineHeight = config.defaultLineHeight
        )
        val position = HologramPosition.fromLocation(location)
        val firstPage = AxoHologramPage(0)
        initialLines.forEach { firstPage.addLine(it) }

        return AxoHologram(
            id = id,
            position = position,
            settings = settings,
            pagesList = listOf(firstPage)
        )
    }
}
