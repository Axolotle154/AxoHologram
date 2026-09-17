package org.axostudio.axohologram.core.hologram.line

import org.axostudio.axohologram.api.hologram.HologramLine
import org.bukkit.entity.Player
import java.util.Locale

object LineManager {

    @JvmStatic
    fun parseLine(raw: String): HologramLine {
        val trimmed = raw.trim()

        if (trimmed.startsWith("item:", ignoreCase = true) || trimmed.startsWith("icon:", ignoreCase = true)) {
            val itemData = trimmed.substring(5).trim()
            return ItemLine(content = itemData)
        }

        if (trimmed.startsWith("block:", ignoreCase = true)) {
            val blockData = trimmed.substring(6).trim()
            return BlockLine(content = blockData)
        }

        if (trimmed.startsWith("text:", ignoreCase = true)) {
            return TextLine(content = trimmed.substring(5))
        }

        if (trimmed.startsWith("composite:", ignoreCase = true)) {
            return CompositeLine(mutableListOf(TextLine(trimmed.substring(10))))
        }

        return TextLine(content = raw)
    }

    @JvmStatic
    fun calculateLineYOffsets(lines: List<HologramLine>, defaultHeight: Double): List<Double> {
        return calculateLineYOffsets(lines, null) { line ->
            if (line.hasHeightOverride()) line.height else defaultHeight
        }
    }

    /**
     * Uses the same layout rule as the previous renderer: only visible lines
     * consume vertical space and the gap between two lines fits the taller one.
     */
    @JvmStatic
    fun calculateLineYOffsets(
        lines: List<HologramLine>,
        player: Player?,
        lineHeight: (HologramLine) -> Double
    ): List<Double> {
        val offsets = MutableList(lines.size) { 0.0 }
        var currentY = 0.0

        for (index in lines.indices) {
            val line = lines[index]
            if (player != null && !line.canView(player)) continue

            offsets[index] = currentY
            val nextVisible = lines.drop(index + 1).firstOrNull { candidate ->
                player == null || candidate.canView(player)
            } ?: continue
            var step = lineHeight(line).coerceAtLeast(0.0)
            if (!line.hasHeightOverride() && !nextVisible.hasHeightOverride()) {
                step = maxOf(step, lineHeight(nextVisible).coerceAtLeast(0.0))
            }
            currentY -= step
        }

        return offsets
    }
}
