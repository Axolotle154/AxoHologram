package org.axostudio.axohologram.core.hologram.model

import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.api.hologram.HologramPage
import org.axostudio.axohologram.core.hologram.line.LineManager
import org.axostudio.axohologram.core.hologram.line.LineType
import org.axostudio.axohologram.core.hologram.line.CompositeLine
import org.axostudio.axohologram.core.hologram.line.TextLine
import java.util.concurrent.CopyOnWriteArrayList

class AxoHologramPage(
    private var index: Int = 0,
    linesList: List<HologramLine> = emptyList()
) : HologramPage {

    private val lines: MutableList<HologramLine> = CopyOnWriteArrayList(linesList)

    override fun getIndex(): Int = index
    override fun setIndex(index: Int) {
        this.index = index
    }

    override fun getLines(): List<HologramLine> = ArrayList(lines)

    override fun getLine(index: Int): HologramLine? {
        if (index in 0 until lines.size) {
            return lines[index]
        }
        return null
    }

    override fun addLine(line: HologramLine) {
        lines.add(line)
    }

    override fun addLine(line: String) {
        lines.add(LineManager.parseLine(line))
    }

    override fun setLine(index: Int, line: HologramLine) {
        if (index in 0 until lines.size) {
            lines[index] = line
        }
    }

    override fun setLine(index: Int, line: String) {
        setLine(index, LineManager.parseLine(line))
    }

    override fun insertLine(index: Int, line: HologramLine) {
        if (index in 0..lines.size) {
            lines.add(index, line)
        }
    }

    override fun removeLine(index: Int) {
        if (index in 0 until lines.size) {
            lines.removeAt(index)
        }
    }

    override fun clearLines() {
        lines.clear()
    }

    override fun lineCount(): Int = lines.size

    override fun isEmpty(): Boolean = lines.isEmpty()

    private var permission: String? = null

    override fun getPermission(): String? = permission
    override fun setPermission(permission: String?) {
        this.permission = permission
    }

    override fun canView(player: org.bukkit.entity.Player?): Boolean {
        if (player == null) return false
        val perm = permission ?: return true
        return player.hasPermission(perm)
    }

    override fun serialize(section: org.bukkit.configuration.ConfigurationSection) {
    }

    override fun clone(): AxoHologramPage {
        val cloned = AxoHologramPage(index)
        for (line in lines) {
            cloned.addLine(cloneLine(line))
        }
        cloned.permission = this.permission
        return cloned
    }

    private fun cloneLine(line: HologramLine): HologramLine {
        val copy = when (line.type) {
            LineType.ITEM -> LineManager.parseLine("item:${line.content}")
            LineType.BLOCK -> LineManager.parseLine("block:${line.content}")
            LineType.COMPOSITE -> CompositeLine().also { it.addSubLine(LineManager.parseLine(line.content)) }
            else -> LineManager.parseLine(line.content)
        }
        copy.offset = line.offset
        if (line.hasHeightOverride()) copy.height = line.height
        if (line.hasBillboardOverride()) copy.billboard = line.billboard
        copy.permission = line.permission
        if (line.hasDisplayAnimationOverride()) copy.displayAnimationOverride = line.displayAnimationOverride
        copy.setScale(line.scaleX, line.scaleY, line.scaleZ)
        return copy
    }
}
