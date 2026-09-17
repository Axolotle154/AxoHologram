package org.axostudio.axohologram.core

import org.axostudio.axohologram.core.hologram.HologramFactory
import org.axostudio.axohologram.core.hologram.HologramManager
import org.axostudio.axohologram.core.hologram.HologramRepository
import org.axostudio.axohologram.core.hologram.model.AxoHologram
import org.axostudio.axohologram.core.hologram.model.HologramPosition
import org.axostudio.axohologram.core.hologram.visibility.VisibilityService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EnableDisableTest {

    @Test
    fun testHologramEnableAndDisable() {
        val repository = HologramRepository()
        val factory = HologramFactory { org.axostudio.axohologram.config.PluginConfig() }
        val visibilityService = VisibilityService()
        val manager = HologramManager(repository, factory, visibilityService)

        var updateTriggered = false
        manager.onHologramUpdated = {
            updateTriggered = true
        }

        val pos = HologramPosition("world", 0.0, 64.0, 0.0, 0f, 0f)
        val holo = AxoHologram("test_toggle", pos)
        manager.registerHologram(holo)
        assertTrue(holo.isEnabled)

        // Disable
        manager.disable(holo)
        assertFalse(holo.isEnabled)
        assertTrue(updateTriggered)

        // Enable
        updateTriggered = false
        manager.enable(holo)
        assertTrue(holo.isEnabled)
        assertTrue(updateTriggered)

        // Test by ID
        assertTrue(manager.disable("test_toggle"))
        assertFalse(holo.isEnabled)

        assertTrue(manager.enable("test_toggle"))
        assertTrue(holo.isEnabled)

        assertFalse(manager.enable("non_existent"))
        assertFalse(manager.disable("non_existent"))
    }
}
