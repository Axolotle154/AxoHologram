package org.axostudio.axohologram.feature.media.image

import org.axostudio.axohologram.feature.media.MapFrameData
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

object ImageRenderer {

    fun loadImage(file: File): BufferedImage? {
        if (!file.exists()) return null
        return runCatching { ImageIO.read(file) }.getOrNull()
    }

    fun loadFromUrl(url: String): BufferedImage? {
        return runCatching { ImageIO.read(URI(url).toURL()) }.getOrNull()
    }

    fun processImageToFrames(image: BufferedImage): MapFrameData {
        return MapFrameData.fromImage(image)
    }
}
