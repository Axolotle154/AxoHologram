package org.axostudio.axohologram.feature.animation

import org.axostudio.axohologram.feature.animation.display.DisplayAnimation
import org.axostudio.axohologram.feature.animation.display.DisplayAnimationFrame
import org.axostudio.axohologram.feature.animation.text.TextAnimationRenderer

class AnimationEngine(
    val registry: AnimationRegistry = AnimationRegistry()
) {
    val textRenderer = TextAnimationRenderer { registry.getTextAnimation(it) }

    fun processText(text: String, currentTick: Long): String {
        return textRenderer.renderAnimations(text, currentTick)
    }

    fun getDisplayFrame(animationName: String, currentTick: Long): DisplayAnimationFrame {
        val animation = registry.getDisplayAnimation(animationName) ?: return DisplayAnimationFrame.IDENTITY
        return animation.getFrame(currentTick)
    }
}
