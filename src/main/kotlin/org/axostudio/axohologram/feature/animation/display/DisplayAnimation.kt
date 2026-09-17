package org.axostudio.axohologram.feature.animation.display

import org.axostudio.axohologram.api.animation.DisplayAnimation as IDisplayAnimation
import org.bukkit.Location
import org.joml.Quaternionf
import org.joml.Vector3f

class DisplayAnimation(
    private val name: String,
    val frames: List<DisplayAnimationFrame>,
    private val tickRate: Int = 2,
    private val enabled: Boolean = true,
    val interpolationDuration: Int = 2,
    val loop: Boolean = true
) : IDisplayAnimation {

    override fun getName(): String = name
    override fun getTickRate(): Int = tickRate
    override fun isEnabled(): Boolean = enabled

    fun getFrame(tick: Long): DisplayAnimationFrame {
        if (frames.isEmpty()) return DisplayAnimationFrame.IDENTITY
        val frameIndex = if (loop) (tick % frames.size).toInt() else tick.coerceAtMost(frames.size - 1L).toInt()
        return frames[frameIndex]
    }

    override fun calculateFrame(baseLocation: Location, tick: Long): IDisplayAnimation.Frame {
        val f = getFrame(tick)
        val loc = baseLocation.clone().add(f.offsetX, f.offsetY, f.offsetZ)
        return IDisplayAnimation.Frame(
            loc,
            Vector3f(f.scaleMultiplier, f.scaleMultiplier, f.scaleMultiplier),
            Quaternionf(),
            interpolationDuration
        )
    }
}
