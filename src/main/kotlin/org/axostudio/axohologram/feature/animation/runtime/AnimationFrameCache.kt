package org.axostudio.axohologram.feature.animation.runtime

import org.axostudio.axohologram.feature.animation.display.DisplayAnimationFrame
import java.util.concurrent.ConcurrentHashMap

class AnimationFrameCache {

    private val cache = ConcurrentHashMap<String, List<DisplayAnimationFrame>>()

    fun getFrames(animationName: String): List<DisplayAnimationFrame>? = cache[animationName.lowercase()]

    fun putFrames(animationName: String, frames: List<DisplayAnimationFrame>) {
        cache[animationName.lowercase()] = frames
    }

    fun clear() {
        cache.clear()
    }
}
