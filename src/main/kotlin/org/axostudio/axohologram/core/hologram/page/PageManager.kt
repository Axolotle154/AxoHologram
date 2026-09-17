package org.axostudio.axohologram.core.hologram.page

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramPage
import org.axostudio.axohologram.core.hologram.model.AxoHologramPage

class PageManager(val controller: PageController = PageController()) {

    fun createPage(index: Int, lines: List<String> = emptyList()): HologramPage {
        val page = AxoHologramPage(index)
        lines.forEach { page.addLine(it) }
        return page
    }

    fun ensureDefaultPage(hologram: Hologram) {
        if (hologram.pageCount() == 0) {
            hologram.addPage(createPage(0))
        }
    }
}
