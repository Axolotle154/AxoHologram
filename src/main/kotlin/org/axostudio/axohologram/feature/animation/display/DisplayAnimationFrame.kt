package org.axostudio.axohologram.feature.animation.display

data class DisplayAnimationFrame(
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val yawOffset: Float = 0.0f,
    val pitchOffset: Float = 0.0f,
    val rollOffset: Float = 0.0f,
    val scaleMultiplier: Float = 1.0f
) {
    companion object {
        @JvmField
        val IDENTITY = DisplayAnimationFrame()
    }
}
