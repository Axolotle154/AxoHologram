package org.axostudio.axohologram.common.text

import org.bukkit.Color
import java.text.Normalizer
import java.util.Locale

object ColorUtil {

    private val TRANSPARENT: Color = Color.fromARGB(0, 0, 0, 0)

    val COMMON_COLOR_SUGGESTIONS: List<String> = listOf(
        "red", "blue", "yellow", "green", "black", "white",
        "gray", "grey", "orange", "purple", "pink", "brown", "transparent"
    )

    @JvmStatic
    fun parseColor(raw: String?): Color {
        require(!raw.isNullOrBlank()) { "Color cannot be empty." }

        var normalized = raw.trim()
        if (isTransparentKeyword(normalized)) {
            return TRANSPARENT
        }

        if (normalized.contains(",")) {
            return parseRgb(normalized)
        }

        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1)
        } else if (normalized.startsWith("0x", ignoreCase = true)) {
            normalized = normalized.substring(2)
        }

        if (normalized.matches(Regex("[0-9a-fA-F]{8}"))) {
            return Color.fromARGB(normalized.toLong(16).toInt())
        }

        if (normalized.matches(Regex("[0-9a-fA-F]{6}"))) {
            return Color.fromRGB(normalized.toInt(16))
        }

        return fromNamedColor(normalized)
    }

    @JvmStatic
    fun isTransparent(color: Color?): Boolean {
        return color != null && color.alpha == 0
    }

    @JvmStatic
    fun toSerializedString(color: Color?): String {
        if (color == null) return "#000000"
        if (isTransparent(color)) return "transparent"
        return if (color.alpha == 255) {
            String.format("#%06X", color.asRGB() and 0x00FFFFFF)
        } else {
            String.format("#%08X", color.asARGB())
        }
    }

    private fun isTransparentKeyword(value: String): Boolean {
        val normalized = normalizeName(value)
        return normalized == "transparent" || normalized == "transparente" ||
                normalized == "none" || normalized == "ninguno"
    }

    private fun parseRgb(raw: String): Color {
        val parts = raw.split(",").map { it.trim() }
        require(parts.size in 3..4) { "RGB format requires 3 or 4 components: $raw" }

        val r = parts[0].toInt().coerceIn(0, 255)
        val g = parts[1].toInt().coerceIn(0, 255)
        val b = parts[2].toInt().coerceIn(0, 255)

        return if (parts.size == 4) {
            val a = parts[3].toInt().coerceIn(0, 255)
            Color.fromARGB(a, r, g, b)
        } else {
            Color.fromRGB(r, g, b)
        }
    }

    private fun fromNamedColor(name: String): Color {
        return when (normalizeName(name)) {
            "red", "rojo" -> Color.RED
            "blue", "azul" -> Color.BLUE
            "green", "verde" -> Color.GREEN
            "yellow", "amarillo" -> Color.YELLOW
            "black", "negro" -> Color.BLACK
            "white", "blanco" -> Color.WHITE
            "gray", "grey", "gris" -> Color.GRAY
            "orange", "naranja" -> Color.ORANGE
            "purple", "morado", "purpura" -> Color.PURPLE
            "pink", "rosa", "rosado" -> Color.fromARGB(255, 255, 105, 180)
            "brown", "marron", "cafe" -> Color.fromARGB(255, 139, 69, 19)
            "aqua", "cyan", "celeste" -> Color.AQUA
            "lime", "lima" -> Color.LIME
            "silver", "plata" -> Color.SILVER
            "navy" -> Color.NAVY
            "olive", "oliva" -> Color.OLIVE
            "maroon" -> Color.MAROON
            "teal" -> Color.TEAL
            "fuchsia" -> Color.FUCHSIA
            else -> throw IllegalArgumentException("Unknown color: $name")
        }
    }

    private fun normalizeName(value: String): String {
        val nfd = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(nfd, "")
    }
}
