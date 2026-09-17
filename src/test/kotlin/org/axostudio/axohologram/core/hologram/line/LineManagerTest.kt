package org.axostudio.axohologram.core.hologram.line

import kotlin.test.Test
import kotlin.test.assertEquals

class LineManagerTest {

    @Test
    fun `uses the taller adjacent default line as the legacy renderer did`() {
        val lines = listOf(
            TextLine("Title"),
            ItemLine("DIAMOND"),
            TextLine("Description")
        )

        val offsets = LineManager.calculateLineYOffsets(lines, null) { line ->
            when (line.type) {
                LineType.ITEM -> 0.65
                else -> 0.25
            }
        }

        assertEquals(listOf(0.0, -0.65, -1.30), offsets)
    }

    @Test
    fun `keeps an explicit line height instead of replacing it with the next default height`() {
        val title = TextLine("Title").also { it.height = 0.10 }
        val item = ItemLine("DIAMOND")

        val offsets = LineManager.calculateLineYOffsets(listOf(title, item), null) { line ->
            if (line.hasHeightOverride()) line.height
            else if (line.type == LineType.ITEM) 0.65
            else 0.25
        }

        assertEquals(listOf(0.0, -0.10), offsets)
    }
}
