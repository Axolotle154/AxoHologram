package org.axostudio.axohologram.feature.animation

import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.feature.animation.display.DisplayAnimation
import org.axostudio.axohologram.feature.animation.display.DisplayAnimationFrame
import org.axostudio.axohologram.feature.animation.runtime.AnimationFrameCache
import org.axostudio.axohologram.feature.animation.runtime.AnimationTickEngine
import org.axostudio.axohologram.feature.animation.text.FrameTextAnimation
import org.axostudio.axohologram.feature.animation.text.ConfiguredTextAnimation
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import kotlin.math.cos
import kotlin.math.sin

class AnimationManager(
    private val configManager: ConfigManager,
    private val scheduler: TaskScheduler,
    val registry: AnimationRegistry = AnimationRegistry(),
    val frameCache: AnimationFrameCache = AnimationFrameCache()
) {
    val engine = AnimationEngine(registry)
    val tickEngine = AnimationTickEngine(scheduler, AnimationSettings.defaults())

    fun init() {
        reload()
        tickEngine.start()
    }

    fun reload() {
        registry.clear()
        frameCache.clear()

        val config = configManager.animationsConfig
        val legacyRoot = config.getConfigurationSection("animations")
        val textSection = config.getConfigurationSection("text-animations") ?: legacyRoot?.getConfigurationSection("text")
        if (textSection != null) {
            for (key in textSection.getKeys(false)) {
                val section = textSection.getConfigurationSection(key) ?: continue
                val frames = section.getStringList("frames")
                if (frames.isNotEmpty()) registry.registerTextAnimation(FrameTextAnimation(key, section.getInt("interval", section.getInt("frame-duration", 4)), frames))
                else registry.registerTextAnimation(ConfiguredTextAnimation(
                    key,
                    section.getString("type", "rainbow") ?: "rainbow",
                    section.getInt("speed", 1),
                    section.getStringList("colors"),
                    section.getString("color", "&f") ?: "&f",
                    section.getString("color1", "&f") ?: "&f",
                    section.getString("color2", "&b") ?: "&b"
                ))
            }
        }

        val customSection = legacyRoot?.getConfigurationSection("custom")
        if (customSection != null) for (key in customSection.getKeys(false)) {
            val section = customSection.getConfigurationSection(key) ?: continue
            if (section.getString("type", "").equals("frame-animation", true)) {
                val frames = section.getStringList("frames")
                if (frames.isNotEmpty()) registry.registerTextAnimation(FrameTextAnimation(key, section.getInt("frame-duration", 1), frames))
            }
        }

        val displaySection = config.getConfigurationSection("display-animations") ?: legacyRoot?.getConfigurationSection("display")
        if (displaySection != null) {
            for (key in displaySection.getKeys(false)) {
                val loop = displaySection.getBoolean("$key.loop", true)
                val interpolation = displaySection.getInt("$key.interpolation-duration", 2)
                val rawFrames = displaySection.getMapList("$key.frames")
                val frames = mutableListOf<DisplayAnimationFrame>()

                for (map in rawFrames) {
                    val ox = (map["x"] as? Number)?.toDouble() ?: 0.0
                    val oy = (map["y"] as? Number)?.toDouble() ?: 0.0
                    val oz = (map["z"] as? Number)?.toDouble() ?: 0.0
                    val yaw = (map["yaw"] as? Number)?.toFloat() ?: 0.0f
                    val pitch = (map["pitch"] as? Number)?.toFloat() ?: 0.0f
                    val roll = (map["roll"] as? Number)?.toFloat() ?: 0.0f
                    val scale = (map["scale"] as? Number)?.toFloat() ?: 1.0f
                    frames.add(DisplayAnimationFrame(ox, oy, oz, yaw, pitch, roll, scale))
                }

                if (frames.isEmpty()) frames += legacyDisplayFrames(displaySection.getConfigurationSection(key))

                if (frames.isNotEmpty()) {
                    registry.registerDisplayAnimation(
                        DisplayAnimation(key, frames, loop = loop, interpolationDuration = interpolation)
                    )
                }
            }
        }

        val presetsSection = config.getConfigurationSection("presets") ?: legacyRoot?.getConfigurationSection("presets")
        if (presetsSection != null) {
            for (key in presetsSection.getKeys(false)) {
                val textAnim = presetsSection.getString("$key.text-animation")
                val displayAnim = presetsSection.getString("$key.display-animation")
                registry.registerPreset(AnimationPreset(key, textAnim, displayAnim))
            }
        }

        val assignments = config.getConfigurationSection("holograms")
        if (assignments != null) for (id in assignments.getKeys(false)) {
            assignments.getString("$id.display-animation")?.takeIf(String::isNotBlank)?.let { registry.assignDisplayAnimation(id, it) }
        }
    }

    fun stop() {
        tickEngine.stop()
        registry.clear()
        frameCache.clear()
    }

    /** Converts 3.x parametric display effects into deterministic render frames. */
    private fun legacyDisplayFrames(section: org.bukkit.configuration.ConfigurationSection?): MutableList<DisplayAnimationFrame> {
        if (section == null) return mutableListOf()
        val type = section.getString("type", "")?.lowercase() ?: return mutableListOf()
        if (type !in setOf("float", "spin", "cinematic-idle", "orbit")) return mutableListOf()
        val speed = section.getDouble("speed", 1.0).coerceAtLeast(0.01)
        val height = section.getDouble("height", section.getDouble("float-height", 0.0)).coerceAtLeast(0.0)
        val radius = section.getDouble("radius", 0.0).coerceAtLeast(0.0)
        val rotationSpeed = section.getDouble("rotation-speed", speed)
        val scaleMin = section.getDouble("scale-min", 1.0).toFloat().coerceAtLeast(0.01f)
        val scaleMax = section.getDouble("scale-max", 1.0).toFloat().coerceAtLeast(scaleMin)
        val axis = section.getString("axis", "y")?.lowercase() ?: "y"
        return MutableList(120) { tick ->
            when (type) {
                "float" -> DisplayAnimationFrame(offsetY = sin(tick * speed * 0.1) * height)
                "spin" -> {
                    val degrees = ((tick * speed * 3.0) % 360.0).toFloat()
                    when (axis) {
                        "x" -> DisplayAnimationFrame(pitchOffset = degrees)
                        "z" -> DisplayAnimationFrame(rollOffset = degrees)
                        else -> DisplayAnimationFrame(yawOffset = degrees)
                    }
                }
                "cinematic-idle" -> {
                    val phase = tick * speed * 0.08
                    val scale = scaleMin + ((sin(phase) + 1.0) * 0.5 * (scaleMax - scaleMin)).toFloat()
                    DisplayAnimationFrame(offsetY = sin(phase) * height, yawOffset = ((tick * rotationSpeed) % 360.0).toFloat(), scaleMultiplier = scale)
                }
                else -> {
                    val phase = tick * speed * 0.08
                    DisplayAnimationFrame(offsetX = cos(phase) * radius, offsetZ = sin(phase) * radius)
                }
            }
        }
    }
}
