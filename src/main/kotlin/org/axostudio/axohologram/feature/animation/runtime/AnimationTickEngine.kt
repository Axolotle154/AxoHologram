package org.axostudio.axohologram.feature.animation.runtime

import org.axostudio.axohologram.feature.animation.AnimationSettings
import org.axostudio.axohologram.feature.animation.display.DisplayAnimationRenderer
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.axostudio.axohologram.platform.scheduler.AxoScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class AnimationTickEngine(
    private val scheduler: TaskScheduler,
    var settings: AnimationSettings = AnimationSettings.defaults()
) {
    private val globalTick = AtomicLong(0)
    private val activeAnimations = ConcurrentHashMap<String, ActiveAnimation>()
    private var taskHandle: AxoScheduler.TaskHandle? = null
    var onTick: ((Long) -> Unit)? = null

    val currentTick: Long
        get() = globalTick.get()

    fun start() {
        stop()
        taskHandle = scheduler.runTimer(settings.tickRate, settings.tickRate) {
            val tick = globalTick.incrementAndGet()
            tickActiveAnimations(tick)
            onTick?.invoke(tick)
        }
    }

    fun stop() {
        taskHandle?.cancel()
        taskHandle = null
        activeAnimations.clear()
    }

    fun registerActiveAnimation(key: String, animation: ActiveAnimation) {
        activeAnimations[key] = animation
    }

    fun unregisterActiveAnimation(key: String) {
        activeAnimations.remove(key)
    }

    private fun tickActiveAnimations(tick: Long) {
        val iterator = activeAnimations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val active = entry.value
            val display = active.displayRef.get()
            if (display == null || !display.isValid) {
                iterator.remove()
                continue
            }

            active.currentTick++
            val frame = active.animation.getFrame(active.currentTick)
            DisplayAnimationRenderer.applyFrame(
                display,
                active.baseLocation,
                frame,
                active.animation.interpolationDuration
            )
        }
    }
}
