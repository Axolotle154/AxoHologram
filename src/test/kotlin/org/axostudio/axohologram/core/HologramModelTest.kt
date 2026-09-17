package org.axostudio.axohologram.core

import org.axostudio.axohologram.api.action.HologramActionType
import org.axostudio.axohologram.api.action.HologramClickType
import org.axostudio.axohologram.core.hologram.action.ActionRegistry
import org.axostudio.axohologram.core.hologram.line.TextLine
import org.axostudio.axohologram.core.hologram.model.AxoHologram
import org.axostudio.axohologram.core.hologram.model.AxoHologramPage
import org.axostudio.axohologram.core.hologram.model.HologramPosition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.bukkit.util.Vector

class HologramModelTest {

    @Test
    fun testHologramCreationAndLineManagement() {
        val position = HologramPosition("world", 100.0, 64.0, 200.0, 0f, 0f)
        val hologram = AxoHologram("test_holo", position)

        assertEquals("test_holo", hologram.id)
        assertEquals("world", hologram.worldName)
        assertEquals(1, hologram.pageCount())

        hologram.addLine("Line 1")
        hologram.addLine("Line 2")

        val page = hologram.getPage(0)
        assertNotNull(page)
        assertEquals(2, page!!.lineCount())
        assertEquals("Line 1", page.getLine(0)?.content)
        assertEquals("Line 2", page.getLine(1)?.content)
    }

    @Test
    fun testPageManipulation() {
        val position = HologramPosition("world", 0.0, 100.0, 0.0, 0f, 0f)
        val hologram = AxoHologram("multi_page", position)

        val page2 = AxoHologramPage(1)
        page2.addLine(TextLine("Page 2 Line 1"))
        hologram.addPage(page2)

        assertEquals(2, hologram.pageCount())
        assertEquals(1, hologram.getPage(1)?.lineCount())
    }

    @Test
    fun testActionsRegistration() {
        val registry = ActionRegistry()
        val action = org.axostudio.axohologram.api.action.HologramAction(
            HologramActionType.COMMAND,
            "say Hello"
        )
        registry.addAction(HologramClickType.RIGHT, action)

        val actions = registry.getActions(HologramClickType.RIGHT)
        assertEquals(1, actions.size)
        assertEquals("say Hello", actions[0].value)
    }

    @Test
    fun testCloneKeepsPageAndLineMetadataWithNewId() {
        val hologram = AxoHologram("original", HologramPosition("world", 0.0, 64.0, 0.0))
        hologram.addLine(TextLine("first"))
        hologram.addPage(AxoHologramPage(1).also { it.addLine(TextLine("second")) })
        hologram.setDefaultPageIndex(1)
        val line = hologram.getPage(0)!!.getLine(0)!!
        line.offset = Vector(1.0, 2.0, 3.0)
        line.permission = "example.view"
        line.setScale(1.5f, 2.0f, 0.5f)

        val clone = hologram.cloneWithId("copy")

        assertEquals("copy", clone.id)
        assertEquals(1, clone.defaultPageIndex)
        val clonedLine = clone.getPage(0)!!.getLine(0)!!
        assertEquals(Vector(1.0, 2.0, 3.0), clonedLine.offset)
        assertEquals("example.view", clonedLine.permission)
        assertEquals(1.5f, clonedLine.scaleX)
        assertEquals(2.0f, clonedLine.scaleY)
        assertEquals(0.5f, clonedLine.scaleZ)
    }
}
