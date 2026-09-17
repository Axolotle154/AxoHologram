package org.axostudio.axohologram.feature.animation

import org.axostudio.axohologram.feature.animation.display.DisplayAnimation
import org.axostudio.axohologram.feature.animation.text.TextAnimation
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AnimationRegistry {

    private val textAnimations = ConcurrentHashMap<String, TextAnimation>()
    private val displayAnimations = ConcurrentHashMap<String, DisplayAnimation>()
    private val presets = ConcurrentHashMap<String, AnimationPreset>()
    private val hologramDisplayAnimations = ConcurrentHashMap<String, String>()

    fun registerTextAnimation(animation: TextAnimation) {
        textAnimations[animation.name.lowercase(Locale.ROOT)] = animation
    }

    fun getTextAnimation(name: String): TextAnimation? = textAnimations[name.lowercase(Locale.ROOT)]

    fun getAllTextAnimations(): Collection<TextAnimation> = textAnimations.values

    fun registerDisplayAnimation(animation: DisplayAnimation) {
        displayAnimations[animation.name.lowercase(Locale.ROOT)] = animation
    }

    fun getDisplayAnimation(name: String): DisplayAnimation? = displayAnimations[name.lowercase(Locale.ROOT)]

    fun getAllDisplayAnimations(): Collection<DisplayAnimation> = displayAnimations.values

    fun registerPreset(preset: AnimationPreset) {
        presets[preset.name.lowercase(Locale.ROOT)] = preset
    }

    fun getPreset(name: String): AnimationPreset? = presets[name.lowercase(Locale.ROOT)]

    fun getAllPresets(): Collection<AnimationPreset> = presets.values

    fun assignDisplayAnimation(hologramId: String, animationName: String) {
        hologramDisplayAnimations[hologramId.lowercase(Locale.ROOT)] = animationName.lowercase(Locale.ROOT)
    }

    fun getAssignedDisplayAnimation(hologramId: String): String? = hologramDisplayAnimations[hologramId.lowercase(Locale.ROOT)]

    fun clear() {
        textAnimations.clear()
        displayAnimations.clear()
        presets.clear()
        hologramDisplayAnimations.clear()
    }
}
