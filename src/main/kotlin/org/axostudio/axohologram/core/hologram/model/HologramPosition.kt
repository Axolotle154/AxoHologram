package org.axostudio.axohologram.core.hologram.model

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.Vector

data class HologramPosition(
    var worldName: String,
    var x: Double,
    var y: Double,
    var z: Double,
    var yaw: Float = 0.0f,
    var pitch: Float = 0.0f
) {
    val world: World?
        get() = runCatching { Bukkit.getWorld(worldName) }.getOrNull()

    fun toLocation(): Location? {
        val w = world ?: return null
        return Location(w, x, y, z, yaw, pitch)
    }

    fun toVector(): Vector = Vector(x, y, z)

    fun clone(): HologramPosition = copy()

    companion object {
        @JvmStatic
        fun fromLocation(location: Location): HologramPosition {
            return HologramPosition(
                worldName = location.world?.name ?: "world",
                x = location.x,
                y = location.y,
                z = location.z,
                yaw = location.yaw,
                pitch = location.pitch
            )
        }
    }
}
