package org.axostudio.axohologram.core.hologram.line

import org.axostudio.axohologram.api.hologram.HologramLine
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.util.Vector

class CompositeLine(
    private val subLines: MutableList<HologramLine> = mutableListOf(),
    private var offset: Vector = Vector(0.0, 0.0, 0.0),
    private var heightOverride: Double? = null,
    private var billboardOverride: Display.Billboard? = null,
    private var permission: String? = null
) : HologramLine {

    fun getSubLines(): List<HologramLine> = subLines

    fun addSubLine(line: HologramLine) {
        subLines.add(line)
    }

    fun removeSubLine(index: Int): HologramLine? {
        if (index in 0 until subLines.size) {
            return subLines.removeAt(index)
        }
        return null
    }

    override fun getType(): LineType = LineType.COMPOSITE

    override fun getContent(): String {
        return subLines.joinToString(" ") { it.content }
    }

    override fun setContent(content: String) {
        if (subLines.isNotEmpty()) {
            subLines[0].content = content
        } else {
            subLines.add(TextLine(content))
        }
    }

    override fun getOffset(): Vector = offset.clone()
    override fun setOffset(offset: Vector) {
        this.offset = offset.clone()
    }

    override fun getHeight(): Double = heightOverride ?: subLines.maxOfOrNull { it.height } ?: 0.3
    override fun setHeight(height: Double) {
        this.heightOverride = height
    }
    override fun clearHeight() {
        this.heightOverride = null
    }
    override fun hasHeightOverride(): Boolean = heightOverride != null

    override fun getBillboard(): Display.Billboard = billboardOverride ?: Display.Billboard.CENTER
    override fun setBillboard(billboard: Display.Billboard) {
        this.billboardOverride = billboard
    }
    override fun hasBillboardOverride(): Boolean = billboardOverride != null

    override fun getPermission(): String? = permission
    override fun setPermission(permission: String?) {
        this.permission = permission
    }

    override fun canView(player: Player?): Boolean {
        if (player == null) return false
        val perm = permission ?: return true
        return perm.isEmpty() || player.hasPermission(perm)
    }
}
