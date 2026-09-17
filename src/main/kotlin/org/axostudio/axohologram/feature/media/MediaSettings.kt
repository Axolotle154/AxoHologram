package org.axostudio.axohologram.feature.media

data class MediaSettings(
    val width: Double = 4.0,
    val height: Double = 3.0,
    val scale: Double = 1.0,
    val renderDistance: Int = 32,
    val maxResolution: Int = 512,
    val lookAtPlayer: Boolean = false,
    val rotation: Float = 0.0f,
    val fps: Int = 15,
    val loop: Boolean = true,
    val autoplay: Boolean = true,
    val maxFrames: Int = 300,
    val maxFileSizeBytes: Long = 50L * 1024L * 1024L
) {
    companion object {
        fun defaultImage(): MediaSettings = MediaSettings(fps = 1, loop = false, autoplay = true)
        fun defaultVideo(): MediaSettings = MediaSettings(fps = 15, loop = true, autoplay = true)
    }
}
