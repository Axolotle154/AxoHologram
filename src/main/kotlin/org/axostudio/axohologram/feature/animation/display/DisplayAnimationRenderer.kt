package org.axostudio.axohologram.feature.animation.display

import org.axostudio.axohologram.common.math.VectorUtil
import org.bukkit.Location
import org.bukkit.entity.Display
import org.joml.Vector3f

object DisplayAnimationRenderer {

    fun applyFrame(display: Display, baseLocation: Location, frame: DisplayAnimationFrame, duration: Int) {
        val rotation = VectorUtil.createRotationQuaternion(frame.pitchOffset, frame.yawOffset, frame.rollOffset)
        val translation = Vector3f(frame.offsetX.toFloat(), frame.offsetY.toFloat(), frame.offsetZ.toFloat())
        val scale = Vector3f(frame.scaleMultiplier, frame.scaleMultiplier, frame.scaleMultiplier)

        display.interpolationDuration = duration
        val transform = org.bukkit.util.Transformation(
            translation,
            rotation,
            scale,
            org.joml.Quaternionf()
        )
        display.transformation = transform
    }
}
