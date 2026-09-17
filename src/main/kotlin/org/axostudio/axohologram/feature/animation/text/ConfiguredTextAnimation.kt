package org.axostudio.axohologram.feature.animation.text

import org.bukkit.entity.Player

/** Text effects compatible with the legacy `animations.text` configuration. */
class ConfiguredTextAnimation(
    private val name: String,
    private val type: String,
    private val speed: Int = 1,
    private val colors: List<String> = emptyList(),
    private val color: String = "&f",
    private val color1: String = "&f",
    private val color2: String = "&b"
) : TextAnimation {
    override fun getName(): String = name
    override fun getSpeed(): Int = speed.coerceAtLeast(1)

    override fun animate(text: String?, viewer: Player?, tick: Long): String {
        val content = text.orEmpty()
        val frame = tick.coerceAtLeast(0) / getSpeed()
        return when (type.lowercase()) {
            "rainbow" -> RAINBOW[(frame % RAINBOW.size).toInt()] + content
            "pulse" -> (colors.ifEmpty { listOf("&f") })[(frame % colors.ifEmpty { listOf("&f") }.size).toInt()] + content
            "matrix" -> color + content
            "wave" -> content.mapIndexed { index, character ->
                if (character.isWhitespace()) character.toString()
                else (if ((index + frame) % 2L == 0L) color1 else color2) + character
            }.joinToString("")
            else -> content
        }
    }

    override fun getCurrentFrame(tick: Long): String = animate("", null, tick)

    private companion object {
        val RAINBOW = listOf("&c", "&6", "&e", "&a", "&b", "&d")
    }
}
