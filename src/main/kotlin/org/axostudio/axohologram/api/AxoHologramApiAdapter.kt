package org.axostudio.axohologram.api

import org.axostudio.axohologram.api.action.HologramAction
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.core.hologram.line.LineManager
import org.bukkit.Location
import java.util.Optional

/** Compatibility facade for plugins compiled against the previous public API. */
class AxoHologramApiAdapter(private val service: HologramService) : AxoHologramAPI {

    override fun holograms(): HologramService = service

    override fun createHologram(id: String, location: Location, lines: List<String>): Hologram =
        service.create(id, location, lines)

    override fun createHologram(id: String, location: Location, lines: List<String>, saveToYaml: Boolean): Hologram =
        service.create(id, location, lines, saveToYaml)

    override fun createHologram(id: String, location: Location, lines: MutableCollection<out HologramLine>): Hologram {
        val hologram = service.create(id, location, emptyList())
        replaceTypedLines(hologram, lines.toList())
        return hologram
    }

    override fun createHologram(id: String, location: Location, lines: MutableCollection<out HologramLine>, saveToYaml: Boolean): Hologram {
        val hologram = service.create(id, location, emptyList(), saveToYaml)
        replaceTypedLines(hologram, lines.toList())
        return hologram
    }

    override fun createItemHologram(id: String, location: Location, itemContent: String): Hologram =
        service.createItem(id, location, itemContent, true)

    override fun createItemHologram(id: String, location: Location, itemContent: String, saveToYaml: Boolean): Hologram =
        service.createItem(id, location, itemContent, saveToYaml)

    override fun createBlockHologram(id: String, location: Location, blockContent: String): Hologram =
        service.createBlock(id, location, blockContent, true)

    override fun createBlockHologram(id: String, location: Location, blockContent: String, saveToYaml: Boolean): Hologram =
        service.createBlock(id, location, blockContent, saveToYaml)

    override fun createTemporaryHologram(location: Location, lines: List<String>): Hologram =
        service.createTemporary(location, lines, -1)

    override fun createTemporaryHologram(id: String, location: Location, lines: List<String>): Hologram =
        service.createTemporary(id, location, lines, -1)

    override fun createTemporaryHologram(location: Location, lines: List<String>, durationTicks: Long): Hologram =
        service.createTemporary(location, lines, durationTicks)

    override fun createTemporaryHologram(id: String, location: Location, lines: List<String>, durationTicks: Long): Hologram =
        service.createTemporary(id, location, lines, durationTicks)

    override fun createTemporaryHologram(location: Location, lines: MutableCollection<out HologramLine>): Hologram =
        createTemporaryHologram(location, lines, -1)

    override fun createTemporaryHologram(id: String, location: Location, lines: MutableCollection<out HologramLine>): Hologram =
        createTemporaryHologram(id, location, lines, -1)

    override fun createTemporaryHologram(location: Location, lines: MutableCollection<out HologramLine>, durationTicks: Long): Hologram {
        val hologram = service.createTemporary(location, emptyList(), durationTicks)
        replaceTypedLines(hologram, lines.toList())
        return hologram
    }

    override fun createTemporaryHologram(id: String, location: Location, lines: MutableCollection<out HologramLine>, durationTicks: Long): Hologram {
        val hologram = service.createTemporary(id, location, emptyList(), durationTicks)
        replaceTypedLines(hologram, lines.toList())
        return hologram
    }

    override fun createTemporaryItemHologram(location: Location, itemContent: String): Hologram =
        createTemporaryItemHologram(location, itemContent, -1)

    override fun createTemporaryItemHologram(id: String, location: Location, itemContent: String): Hologram =
        createTemporaryItemHologram(id, location, itemContent, -1)

    override fun createTemporaryItemHologram(location: Location, itemContent: String, durationTicks: Long): Hologram =
        service.createTemporary(location, listOf("item:$itemContent"), durationTicks)

    override fun createTemporaryItemHologram(id: String, location: Location, itemContent: String, durationTicks: Long): Hologram =
        service.createTemporary(id, location, listOf("item:$itemContent"), durationTicks)

    override fun createTemporaryBlockHologram(location: Location, blockContent: String): Hologram =
        createTemporaryBlockHologram(location, blockContent, -1)

    override fun createTemporaryBlockHologram(id: String, location: Location, blockContent: String): Hologram =
        createTemporaryBlockHologram(id, location, blockContent, -1)

    override fun createTemporaryBlockHologram(location: Location, blockContent: String, durationTicks: Long): Hologram =
        service.createTemporary(location, listOf("block:$blockContent"), durationTicks)

    override fun createTemporaryBlockHologram(id: String, location: Location, blockContent: String, durationTicks: Long): Hologram =
        service.createTemporary(id, location, listOf("block:$blockContent"), durationTicks)

    override fun deleteHologram(id: String): Boolean = service.delete(id)
    override fun deleteHologram(hologram: Hologram): Boolean = service.delete(hologram)
    override fun exists(id: String): Boolean = service.exists(id)
    override fun getHologram(id: String): Optional<Hologram> = service.get(id)
    override fun getHolograms(): MutableCollection<Hologram> = service.getAll().toMutableList()

    override fun updateLines(id: String, lines: List<String>) = replaceTextLines(service.require(id), lines)
    override fun updateLines(hologram: Hologram, lines: List<String>) = replaceTextLines(hologram, lines)
    override fun updateLines(id: String, lines: MutableCollection<out HologramLine>) = replaceTypedLines(service.require(id), lines.toList())
    override fun updateLines(hologram: Hologram, lines: MutableCollection<out HologramLine>) = replaceTypedLines(hologram, lines.toList())

    override fun updateItemLine(id: String, itemContent: String) = replaceTypedLines(service.require(id), listOf(createItemLine(itemContent)))
    override fun updateItemLine(hologram: Hologram, itemContent: String) = replaceTypedLines(hologram, listOf(createItemLine(itemContent)))
    override fun updateBlockLine(id: String, blockContent: String) = replaceTypedLines(service.require(id), listOf(createBlockLine(blockContent)))
    override fun updateBlockLine(hologram: Hologram, blockContent: String) = replaceTypedLines(hologram, listOf(createBlockLine(blockContent)))

    override fun addLine(id: String, line: String) = changed(service.require(id)) { it.addTextLine(line) }
    override fun addLine(hologram: Hologram, line: String) = changed(hologram) { it.addTextLine(line) }
    override fun addLines(id: String, lines: List<String>) = changed(service.require(id)) { it.addTextLines(lines) }
    override fun addLines(hologram: Hologram, lines: List<String>) = changed(hologram) { it.addTextLines(lines) }
    override fun addTextLine(id: String, line: String) = changed(service.require(id)) { it.addTextLine(line) }
    override fun addTextLine(hologram: Hologram, line: String) = changed(hologram) { it.addTextLine(line) }
    override fun addTextLines(id: String, lines: List<String>) = changed(service.require(id)) { it.addTextLines(lines) }
    override fun addTextLines(hologram: Hologram, lines: List<String>) = changed(hologram) { it.addTextLines(lines) }
    override fun addItemLine(id: String, itemContent: String) = changed(service.require(id)) { it.addLine(createItemLine(itemContent)) }
    override fun addItemLine(hologram: Hologram, itemContent: String) = changed(hologram) { it.addLine(createItemLine(itemContent)) }
    override fun addBlockLine(id: String, blockContent: String) = changed(service.require(id)) { it.addLine(createBlockLine(blockContent)) }
    override fun addBlockLine(hologram: Hologram, blockContent: String) = changed(hologram) { it.addLine(createBlockLine(blockContent)) }
    override fun addLine(id: String, line: HologramLine) = changed(service.require(id)) { it.addLine(line) }
    override fun addLine(hologram: Hologram, line: HologramLine) = changed(hologram) { it.addLine(line) }
    override fun addLines(id: String, lines: MutableCollection<out HologramLine>) = changed(service.require(id)) { it.addLines(lines.toList()) }
    override fun addLines(hologram: Hologram, lines: MutableCollection<out HologramLine>) = changed(hologram) { it.addLines(lines.toList()) }

    override fun createItemLine(itemContent: String): HologramLine = LineManager.parseLine("item:$itemContent")
    override fun createTextLine(textContent: String): HologramLine = LineManager.parseLine(textContent)
    override fun createBlockLine(blockContent: String): HologramLine = LineManager.parseLine("block:$blockContent")

    override fun getHeight(id: String): Double = service.require(id).height
    override fun getHeight(id: String, pageIndex: Int): Double = service.require(id).getPage(pageIndex)?.let { service.require(id).height } ?: 0.0
    override fun getHeight(hologram: Hologram): Double = hologram.height
    override fun getHeight(hologram: Hologram, pageIndex: Int): Double = hologram.getPage(pageIndex)?.let { hologram.height } ?: 0.0
    override fun getLineHeight(hologram: Hologram, line: HologramLine): Double = if (line.hasHeightOverride()) line.height else hologram.height
    override fun teleportHologram(id: String, location: Location) = service.require(id).setLocation(location)
    override fun teleportHologram(hologram: Hologram, location: Location) = hologram.setLocation(location)
    override fun showHologram(id: String) = service.show(service.require(id))
    override fun showHologram(hologram: Hologram) = service.show(hologram)
    override fun hideHologram(id: String) = service.hide(service.require(id))
    override fun hideHologram(hologram: Hologram) = service.hide(hologram)

    private fun replaceTypedLines(hologram: Hologram, lines: List<HologramLine>) {
        val page = hologram.getPage(0) ?: return
        page.clearLines()
        lines.forEach(page::addLine)
        service.saveAll()
        service.update(hologram)
    }

    private fun replaceTextLines(hologram: Hologram, lines: List<String>) {
        val page = hologram.getPage(0) ?: return
        page.clearLines()
        lines.forEach(page::addLine)
        service.saveAll()
        service.update(hologram)
    }

    private fun changed(hologram: Hologram, mutation: (Hologram) -> Unit) {
        mutation(hologram)
        service.saveAll()
        service.update(hologram)
    }
}
