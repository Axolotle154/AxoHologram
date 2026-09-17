package org.axostudio.axohologram.feature.media

import org.bukkit.map.MapPalette
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.ArrayList
import kotlin.math.ceil
import kotlin.math.max

data class MapFrameData(
    val width: Int,
    val height: Int,
    val columns: Int,
    val rows: Int,
    val tiles: List<ByteArray>
) {
    fun tile(column: Int, row: Int): ByteArray {
        val index = row * columns + column
        return tiles[index]
    }

    val tileCount: Int
        get() = tiles.size

    companion object {
        private const val MAP_SIZE = 128

        @Suppress("DEPRECATION")
        fun fromImage(image: BufferedImage): MapFrameData {
            val columns = max(1, ceil(image.width.toDouble() / MAP_SIZE).toInt())
            val rows = max(1, ceil(image.height.toDouble() / MAP_SIZE).toInt())
            val tiles = ArrayList<ByteArray>(columns * rows)
            val tile = BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB)
            val graphics = tile.createGraphics()

            try {
                for (row in 0 until rows) {
                    for (col in 0 until columns) {
                        graphics.composite = AlphaComposite.Clear
                        graphics.fillRect(0, 0, MAP_SIZE, MAP_SIZE)
                        graphics.composite = AlphaComposite.SrcOver
                        graphics.drawImage(image, -col * MAP_SIZE, -row * MAP_SIZE, null)
                        tiles.add(MapPalette.imageToBytes(tile))
                    }
                }
            } finally {
                graphics.dispose()
            }

            return MapFrameData(image.width, image.height, columns, rows, tiles)
        }
    }
}
