package org.axostudio.axohologram.persistence.yaml

import org.axostudio.axohologram.api.action.HologramAction
import org.axostudio.axohologram.api.action.HologramActionType
import org.axostudio.axohologram.api.action.HologramClickType
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.common.text.ColorUtil
import org.axostudio.axohologram.core.hologram.model.AxoHologram
import org.axostudio.axohologram.core.hologram.model.AxoHologramPage
import org.axostudio.axohologram.core.hologram.model.HologramPosition
import org.axostudio.axohologram.core.hologram.model.HologramSettings
import org.axostudio.axohologram.core.hologram.visibility.VisibilityMode
import org.axostudio.axohologram.core.hologram.line.LineManager
import org.axostudio.axohologram.core.hologram.line.ItemLine
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.Locale

object HologramDeserializer {

    fun deserialize(id: String, config: ConfigurationSection): Hologram {
        val worldName = config.getString("location.world") ?: "world"
        val x = config.getDouble("location.x", 0.0)
        val y = config.getDouble("location.y", 64.0)
        val z = config.getDouble("location.z", 0.0)
        val yaw = config.getDouble("location.yaw", 0.0).toFloat()
        val pitch = config.getDouble("location.pitch", 0.0).toFloat()

        val position = HologramPosition(worldName, x, y, z, yaw, pitch)

        val settings = HologramSettings(
            group = config.getString("group", "") ?: "",
            permission = config.getString("permission"),
            visibilityMode = VisibilityMode.fromString(config.getString("visibility") ?: config.getString("visibility.mode") ?: if (!config.getString("permission").isNullOrBlank()) "PERMISSION" else "ALL"),
            viewDistance = config.getInt("view-distance", config.getInt("visibility.distance", config.getInt("visibility_distance", 48))),
            lineHeight = config.getDouble("line-height", 0.25),
            billboard = runCatching {
                Display.Billboard.valueOf((config.getString("billboard", "CENTER") ?: "CENTER").uppercase(Locale.ROOT))
            }.getOrDefault(Display.Billboard.CENTER),
            scaleX = config.getDouble("scale.x", config.getDouble("scale_x", config.getDouble("scale", 1.0))).toFloat(),
            scaleY = config.getDouble("scale.y", config.getDouble("scale_y", config.getDouble("scale", 1.0))).toFloat(),
            scaleZ = config.getDouble("scale.z", config.getDouble("scale_z", config.getDouble("scale", 1.0))).toFloat(),
            shadowRadius = config.getDouble("shadow.radius", config.getDouble("shadow_radius", 0.0)).toFloat(),
            shadowStrength = config.getDouble("shadow.strength", config.getDouble("shadow_strength", 1.0)).toFloat(),
            brightnessBlock = config.getInt("brightness.block", -1).coerceIn(-1, 15),
            brightnessSky = config.getInt("brightness.sky", -1).coerceIn(-1, 15),
            backgroundColor = runCatching { ColorUtil.parseColor(config.getString("background-color") ?: config.getString("background") ?: config.getString("style.background")) }.getOrDefault(ColorUtil.parseColor("transparent")),
            textShadow = config.getBoolean("text-shadow", config.getBoolean("text_shadow", config.getBoolean("style.text-shadow", true))),
            seeThrough = config.getBoolean("see-through", config.getBoolean("see_through", config.getBoolean("style.see-through", false))),
            alignment = runCatching {
                TextDisplay.TextAlignment.valueOf((config.getString("alignment") ?: config.getString("text_alignment") ?: config.getString("style.alignment", "CENTER") ?: "CENTER").uppercase(Locale.ROOT))
            }.getOrDefault(TextDisplay.TextAlignment.CENTER),
            updateTextInterval = config.getLong("update-text-interval", config.getLong("text.update-interval", 20L)),
            displayAnimation = config.getString("animation.display") ?: config.getString("display.animation") ?: config.getString("display-animation"),
            displayAnimationEnabled = config.getBoolean("animation.enabled", config.getBoolean("display.animation.enabled", config.getBoolean("animation.display-enabled", config.getBoolean("display-animation-enabled", false)))),
            linkedNpc = config.getString("npc.linked") ?: config.getString("linked-npc") ?: config.getString("linkedNpc"),
            persistent = config.getBoolean("persistent", true),
            enabled = config.getBoolean("enabled", true)
        )

        val pages = mutableListOf<AxoHologramPage>()
        val pagesList = config.getMapList("pages")
        if (pagesList.isNotEmpty()) {
            for ((idx, pageMap) in pagesList.withIndex()) {
                val page = AxoHologramPage(idx)
                val lines = pageMap["lines"] as? List<*> ?: emptyList<Any>()
                for (raw in lines) {
                    if (raw is Map<*, *>) {
                        val type = raw["type"]?.toString()?.lowercase(Locale.ROOT) ?: "text"
                        val textEntries = raw["text"].asStringList()
                        if (type == "text" && textEntries.isNotEmpty()) {
                            textEntries.forEach { page.addLine(it) }
                            continue
                        }
                        val content = raw["content"]?.toString() ?: raw["text"]?.toString() ?: raw["item"]?.toString() ?: raw["block"]?.toString() ?: ""
                        if (type == "composite") {
                            // The new renderer represents a legacy composite as individual lines.
                            // This retains every segment even though packet IDs are now per line.
                            (raw["segments"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { segment ->
                                val segmentType = segment["type"]?.toString()?.lowercase(Locale.ROOT) ?: "text"
                                val segmentContent = segment["content"]?.toString() ?: segment["text"]?.toString() ?: segment["item"]?.toString() ?: ""
                                page.addLine(LineManager.parseLine(if (segmentType == "text") segmentContent else "$segmentType:$segmentContent"))
                            }
                            continue
                        }
                        val line = LineManager.parseLine(if (type == "text") content else "$type:$content")
                        line.offset = Vector(
                            raw["offset.x"].numberValue(raw.nestedNumber("offset", "x")), raw["offset.y"].numberValue(raw.nestedNumber("offset", "y")), raw["offset.z"].numberValue(raw.nestedNumber("offset", "z"))
                        )
                        if (raw.containsKey("height")) line.height = raw["height"].numberValue()
                        if (raw.containsKey("permission")) line.permission = raw["permission"]?.toString()
                        if (raw.containsKey("billboard")) runCatching {
                            line.billboard = Display.Billboard.valueOf(raw["billboard"].toString().uppercase(Locale.ROOT))
                        }
                        val animation = raw["animation"] ?: raw["display-animation"]
                        if (animation != null && !animation.toString().equals("none", true)) line.displayAnimationOverride = animation.toString()
                        line.setScale(raw["scale.x"].numberValue(raw.nestedNumber("scale", "x", 1.0)).toFloat(), raw["scale.y"].numberValue(raw.nestedNumber("scale", "y", 1.0)).toFloat(), raw["scale.z"].numberValue(raw.nestedNumber("scale", "z", 1.0)).toFloat())
                        if (line is ItemLine) {
                            raw["item"].toItemStack()?.let(line::setItemStack)
                            val frameList = (raw["item-animation.frames"] ?: (raw["item-animation"] as? Map<*, *>)?.get("frames")).asStringList()
                            if (frameList.isNotEmpty()) {
                                val duration = raw["item-animation.frame-duration"].numberValue((raw["item-animation"] as? Map<*, *>)?.get("frame-duration").numberValue(1.0)).toInt()
                                val loop = (raw["item-animation.loop"] ?: (raw["item-animation"] as? Map<*, *>)?.get("loop")) as? Boolean ?: true
                                line.setItemAnimation(frameList, duration, loop)
                            }
                        }
                        page.addLine(line)
                    } else if (raw != null) {
                        page.addLine(raw.toString())
                    }
                }
                page.permission = pageMap["permission"]?.toString()
                pages.add(page)
            }
        } else {
            val simpleType = config.getString("type")?.lowercase(Locale.ROOT)
            val simpleValue = when (simpleType) {
                "item" -> "item:${config.getString("item", "STONE")}"
                "block" -> "block:${config.getString("block", "STONE")}"
                else -> null
            }
            val lines = if (simpleValue != null) listOf(simpleValue)
            // Legacy 3.x holograms store multiple text lines under `text:`.
            // getString() coerces that list to one literal value, so animations
            // and line spacing were lost. Keep every entry as an individual line.
            else if (config.isSet("text")) {
                config.getStringList("text").takeIf { it.isNotEmpty() }
                    ?: listOf(config.getString("text", "") ?: "")
            }
            else config.getStringList("lines")
            val page = AxoHologramPage(0)
            lines.forEach { page.addLine(it) }
            pages.add(page)
        }

        val hologram = AxoHologram(id, position, settings, pages)
        hologram.setOffset(Vector(
            config.getDouble("translation.x", config.getDouble("offset.x", 0.0)),
            config.getDouble("translation.y", config.getDouble("offset.y", 0.0)),
            config.getDouble("translation.z", config.getDouble("offset.z", 0.0))
        ))
        hologram.setDefaultPageIndex((config.getInt("default-page", 1) - 1).coerceAtLeast(0))

        // Actions
        val actionsSection = config.getConfigurationSection("actions")
        if (actionsSection != null) {
            for (clickKey in actionsSection.getKeys(false)) {
                val clickType = runCatching { HologramClickType.valueOf(clickKey.uppercase(Locale.ROOT)) }.getOrNull() ?: continue
                val actionList = actionsSection.getMapList(clickKey)
                for (actionMap in actionList) {
                    val typeStr = actionMap["type"]?.toString() ?: continue
                    val valStr = actionMap["value"]?.toString() ?: continue
                    val actType = runCatching { HologramActionType.valueOf(typeStr.uppercase(Locale.ROOT)) }.getOrNull() ?: continue
                    hologram.addAction(clickType, HologramAction(actType, valStr))
                }
            }
        }

        return hologram
    }
}

private fun Any?.numberValue(default: Double = 0.0): Double = (this as? Number)?.toDouble() ?: default

private fun Map<*, *>.nestedNumber(parent: String, key: String, default: Double = 0.0): Double =
    ((this[parent] as? Map<*, *>)?.get(key) as? Number)?.toDouble() ?: default

private fun Any?.asStringList(): List<String> = when (this) {
    is List<*> -> map { it?.toString().orEmpty() }
    is String -> listOf(this)
    else -> emptyList()
}

@Suppress("UNCHECKED_CAST")
private fun Any?.toItemStack(): ItemStack? = when (this) {
    is ItemStack -> clone()
    is Map<*, *> -> runCatching { ItemStack.deserialize(this.entries.associate { it.key.toString() to it.value } as Map<String, Any>) }.getOrNull()
    else -> null
}
