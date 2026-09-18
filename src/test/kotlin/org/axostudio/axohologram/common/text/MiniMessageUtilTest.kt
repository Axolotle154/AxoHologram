package org.axostudio.axohologram.common.text

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniMessageUtilTest {
    @Test
    fun `detects PlaceholderAPI tokens`() {
        assertTrue(MiniMessageUtil.containsPlaceholderApiToken("Jugador: %player_name%"))
        assertTrue(MiniMessageUtil.containsPlaceholderApiToken("%server_online%"))
    }

    @Test
    fun `ignores ordinary text and incomplete tokens`() {
        assertFalse(MiniMessageUtil.containsPlaceholderApiToken("Jugador: Alex"))
        assertFalse(MiniMessageUtil.containsPlaceholderApiToken("Jugador: %player_name"))
        assertFalse(MiniMessageUtil.containsPlaceholderApiToken(null))
    }
}
