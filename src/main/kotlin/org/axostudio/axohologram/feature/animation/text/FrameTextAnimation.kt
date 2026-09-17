package org.axostudio.axohologram.feature.animation.text

import org.bukkit.entity.Player

class FrameTextAnimation(
    private val name: String,
    private val intervalTicks: Int = 4,
    val frames: List<String>
) : TextAnimation {

    override fun getName(): String = name
    override fun getSpeed(): Int = intervalTicks

    override fun animate(text: String?, viewer: Player?, tick: Long): String {
        val current = getCurrentFrame(tick)
        return current.replace("{text}", text.orEmpty())
    }

    fun getIntervalTicks(): Int = intervalTicks
    fun getFrameCount(): Int = frames.size

    fun getFrame(index: Int): String {
        if (frames.isEmpty()) return ""
        return frames[index.coerceIn(0, frames.size - 1)]
    }

    override fun getCurrentFrame(tick: Long): String {
        if (frames.isEmpty()) return ""
        val step = if (intervalTicks <= 1) tick else tick / intervalTicks
        val frameIndex = (step % frames.size).toInt()
        return frames[frameIndex]
    }
}
