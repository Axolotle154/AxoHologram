package org.axostudio.axohologram.core.hologram.model

import org.axostudio.axohologram.core.hologram.visibility.VisibilityMode
import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay

data class HologramSettings(
    var group: String = "",
    var permission: String? = null,
    var visibilityMode: VisibilityMode = VisibilityMode.ALL,
    var viewDistance: Int = 48,
    var scaleX: Float = 1.0f,
    var scaleY: Float = 1.0f,
    var scaleZ: Float = 1.0f,
    var lineHeight: Double = 0.25,
    var billboard: Display.Billboard = Display.Billboard.CENTER,
    var shadowRadius: Float = 0.0f,
    var shadowStrength: Float = 1.0f,
    var brightnessBlock: Int = -1,
    var brightnessSky: Int = -1,
    var backgroundColor: Color = Color.fromARGB(0, 0, 0, 0),
    var textShadow: Boolean = true,
    var seeThrough: Boolean = false,
    var alignment: TextDisplay.TextAlignment = TextDisplay.TextAlignment.CENTER,
    var updateTextInterval: Long = 20L,
    var displayAnimation: String? = null,
    var displayAnimationEnabled: Boolean = false,
    var linkedNpc: String? = null,
    var persistent: Boolean = true,
    var enabled: Boolean = true
) {
    fun clone(): HologramSettings = copy()
}
