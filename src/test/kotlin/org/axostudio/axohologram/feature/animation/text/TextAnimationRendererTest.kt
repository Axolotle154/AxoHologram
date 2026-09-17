package org.axostudio.axohologram.feature.animation.text

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class TextAnimationRendererTest {
    @Test
    fun replacesTheWholePairedAnimationTag() {
        val animation = FrameTextAnimation("cycle", 1, listOf("&c{text}"))
        val renderer = TextAnimationRenderer { if (it == "cycle") animation else null }

        assertEquals("&cHello", renderer.renderAnimations("<anim:cycle>Hello</anim:cycle>", 0))
    }
}
