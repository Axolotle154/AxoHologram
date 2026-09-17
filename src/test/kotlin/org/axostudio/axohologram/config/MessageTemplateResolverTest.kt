package org.axostudio.axohologram.config

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageTemplateResolverTest {
    @Test
    fun `expands both supported prefix placeholders`() {
        val prefix = "<aqua>Axo</aqua> <gray>»</gray> "

        assertEquals(
            "${prefix}<green>Reloaded</green>",
            MessageTemplateResolver.resolve("<prefix><green>Reloaded</green>", prefix)
        )
        assertEquals("${prefix}Reloaded", MessageTemplateResolver.resolve("%prefix%Reloaded", prefix))
    }
}
