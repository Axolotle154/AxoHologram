package org.axostudio.axohologram.feature.media.map

import org.axostudio.axohologram.feature.media.MapFrameData
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import java.util.concurrent.ConcurrentHashMap

object MapFrameRenderer {

    private val frames = ConcurrentHashMap<Int, ByteArray>()
    private val renderers = ConcurrentHashMap<Int, MapRenderer>()

    fun sendMapData(player: Player, mapId: Int, mapData: ByteArray) {
        if (!player.isOnline || mapId !in 0..Short.MAX_VALUE) return
        val map = updateMapData(mapId, mapData) ?: return
        player.sendMap(map)
    }

    /** Updates a map view globally; clients holding its filled-map item receive the next render. */
    fun updateMapData(mapId: Int, mapData: ByteArray): MapView? {
        if (mapId !in 0..Short.MAX_VALUE) return null
        val map = Bukkit.getMap(mapId) ?: return null
        frames[mapId] = mapData.copyOf()
        val renderer = renderers.computeIfAbsent(mapId) {
            object : MapRenderer() {
                override fun render(view: MapView, canvas: MapCanvas, viewer: Player) {
                    val pixels = frames[mapId] ?: return
                    val size = minOf(128 * 128, pixels.size)
                    for (i in 0 until size) canvas.setPixel(i % 128, i / 128, pixels[i])
                }
            }
        }
        if (!map.renderers.contains(renderer)) map.addRenderer(renderer)
        return map
    }

    fun renderFrame(player: Player, baseMapId: Int, frameData: MapFrameData) {
        for (i in 0 until frameData.tileCount) {
            sendMapData(player, baseMapId + i, frameData.tiles[i])
        }
    }
}
