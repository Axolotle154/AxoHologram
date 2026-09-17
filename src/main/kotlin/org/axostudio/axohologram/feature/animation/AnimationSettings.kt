package org.axostudio.axohologram.feature.animation

data class AnimationSettings(
    val enabled: Boolean = true,
    val tickRate: Long = 2L,
    val cacheFrames: Boolean = true,
    val asyncTextProcessing: Boolean = true,
    val displayInterpolation: Boolean = true
) {
    val interpolationDuration: Int
        get() = if (displayInterpolation) tickRate.coerceIn(1L, 20L).toInt() else 0

    companion object {
        fun defaults(): AnimationSettings = AnimationSettings()
    }
}
