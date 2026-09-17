package org.axostudio.axohologram.feature.media.video

import org.axostudio.axohologram.feature.media.MapFrameData
import java.awt.image.BufferedImage
import java.io.File

class VideoRenderer {

    private val cachedFrames = mutableListOf<MapFrameData>()

    fun loadFrames(frames: List<BufferedImage>) {
        cachedFrames.clear()
        for (img in frames) {
            cachedFrames.add(MapFrameData.fromImage(img))
        }
    }

    fun getFrame(frameIndex: Int): MapFrameData? {
        if (cachedFrames.isEmpty()) return null
        return cachedFrames[frameIndex % cachedFrames.size]
    }

    val totalFrames: Int
        get() = cachedFrames.size

    fun clear() {
        cachedFrames.clear()
    }
}
