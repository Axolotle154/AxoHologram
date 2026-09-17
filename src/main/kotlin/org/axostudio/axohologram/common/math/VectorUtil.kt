package org.axostudio.axohologram.common.math

import org.bukkit.Location
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.*

object VectorUtil {

    @JvmStatic
    fun toJoml(vector: Vector): Vector3f {
        return Vector3f(vector.x.toFloat(), vector.y.toFloat(), vector.z.toFloat())
    }

    @JvmStatic
    fun toBukkit(vector: Vector3f): Vector {
        return Vector(vector.x.toDouble(), vector.y.toDouble(), vector.z.toDouble())
    }

    @JvmStatic
    fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + fraction * (end - start)
    }

    @JvmStatic
    fun lerpVector(start: Vector3f, end: Vector3f, fraction: Float): Vector3f {
        return Vector3f(
            lerp(start.x, end.x, fraction),
            lerp(start.y, end.y, fraction),
            lerp(start.z, end.z, fraction)
        )
    }

    @JvmStatic
    fun createRotationQuaternion(pitch: Float, yaw: Float, roll: Float): Quaternionf {
        return Quaternionf().rotationYXZ(
            Math.toRadians(-yaw.toDouble()).toFloat(),
            Math.toRadians(pitch.toDouble()).toFloat(),
            Math.toRadians(roll.toDouble()).toFloat()
        )
    }

    @JvmStatic
    fun distanceSquared(loc1: Location, loc2: Location): Double {
        if (loc1.world != loc2.world) return Double.MAX_VALUE
        val dx = loc1.x - loc2.x
        val dy = loc1.y - loc2.y
        val dz = loc1.z - loc2.z
        return dx * dx + dy * dy + dz * dz
    }
}
