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

    @Test
    fun `adds the prefix to legacy custom messages without a token`() {
        val prefix = "<aqua>Axo</aqua> <gray>»</gray> "

        assertEquals(
            "${prefix}<red>Custom message</red>",
            MessageTemplateResolver.resolve("<red>Custom message</red>", prefix)
        )
    }
}
