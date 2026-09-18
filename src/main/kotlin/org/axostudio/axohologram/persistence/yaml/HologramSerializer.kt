package org.axostudio.axohologram.persistence.yaml

import org.axostudio.axohologram.api.action.HologramClickType
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.common.text.ColorUtil
import org.axostudio.axohologram.core.hologram.line.ItemLine
import org.axostudio.axohologram.core.hologram.line.LineType
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.util.Vector

object HologramSerializer {

    fun serialize(hologram: Hologram): YamlConfiguration {
        val config = YamlConfiguration()

        if (hologram is org.axostudio.axohologram.core.hologram.model.AxoHologram) {
            val pos = hologram.position
            config.set("location.world", pos.worldName)
            config.set("location.x", pos.x)
            config.set("location.y", pos.y)
            config.set("location.z", pos.z)
            config.set("location.yaw", pos.yaw)
            config.set("location.pitch", pos.pitch)
        } else {
            val loc = hologram.location
            if (loc != null) {
                config.set("location.world", loc.world?.name ?: "world")
                config.set("location.x", loc.x)
                config.set("location.y", loc.y)
                config.set("location.z", loc.z)
                config.set("location.yaw", loc.yaw)
                config.set("location.pitch", loc.pitch)
            }
        }

        config.set("enabled", if (hologram.isEnabled) null else false)
        config.set("persistent", hologram.isPersistent)
        val offset = hologram.offset
        config.set("translation.x", offset.x)
        config.set("translation.y", offset.y)
        config.set("translation.z", offset.z)

        if (hologram.group.isNotBlank()) config.set("group", hologram.group)
        if (!hologram.permission.isNullOrBlank()) config.set("permission", hologram.permission)
        config.set("visibility", hologram.visibilityMode.name)
        config.set("view-distance", hologram.viewDistance)
        config.set("line-height", hologram.height)
        config.set("billboard", hologram.billboard.name)
        config.set("scale.x", hologram.scaleX)
        config.set("scale.y", hologram.scaleY)
        config.set("scale.z", hologram.scaleZ)
        config.set("shadow.radius", hologram.shadowRadius)
        config.set("shadow.strength", hologram.shadowStrength)
        config.set("background-color", ColorUtil.toSerializedString(hologram.backgroundColor))
        config.set("text-shadow", hologram.hasTextShadow())
        config.set("see-through", hologram.isSeeThrough)
        config.set("alignment", hologram.alignment.name)
        config.set("update-text-interval", hologram.updateTextInterval)

        if (!hologram.displayAnimation.isNullOrBlank()) {
            config.set("animation.display", hologram.displayAnimation)
            config.set("animation.enabled", hologram.isDisplayAnimationEnabled)
        }
        if (!hologram.linkedNpc.isNullOrBlank()) {
            config.set("npc.linked", hologram.linkedNpc)
        }
        config.set("default-page", hologram.defaultPageIndex + 1)

        // Pages and lines
        val pagesList = mutableListOf<Map<String, Any>>()
        for (page in hologram.pages) {
            val pageMap = mutableMapOf<String, Any>()
            pageMap["lines"] = serializeLines(page.lines)
            if (!page.permission.isNullOrBlank()) pageMap["permission"] = page.permission!!
            pagesList.add(pageMap)
        }
        config.set("pages", pagesList)

        // Actions
        val actionsMap = mutableMapOf<String, List<Map<String, String>>>()
        for (type in HologramClickType.values()) {
            val actions = hologram.getActions(type)
            if (actions.isNotEmpty()) {
                actionsMap[type.name.lowercase()] = actions.map { mapOf("type" to it.type.name, "value" to it.value) }
            }
        }
        if (actionsMap.isNotEmpty()) {
            config.set("actions", actionsMap)
        }

        return config
    }

    /**
     * Keep simple text pages compatible with the old, compact YAML format:
     *
     *   - type: TEXT
     *     text:
     *       - 'first line'
     *       - 'second line'
     *
     * Lines with individual settings still use the detailed representation so
     * serialization never drops offsets, scales, permissions or animations.
     */
    private fun serializeLines(lines: List<HologramLine>): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        val textBuffer = mutableListOf<String>()

        fun flushText() {
            if (textBuffer.isNotEmpty()) {
                result += linkedMapOf<String, Any>(
                    "type" to LineType.TEXT.name,
                    "text" to textBuffer.toList()
                )
                textBuffer.clear()
            }
        }

        for (line in lines) {
            if (line.type == LineType.TEXT && isSimpleTextLine(line)) {
                textBuffer += line.content
            } else {
                flushText()
                result += serializeDetailedLine(line)
            }
        }
        flushText()
        return result
    }

    private fun isSimpleTextLine(line: HologramLine): Boolean =
        line.offset == Vector(0.0, 0.0, 0.0) &&
            line.scaleX == 1.0f && line.scaleY == 1.0f && line.scaleZ == 1.0f &&
            !line.hasHeightOverride() &&
            !line.hasBillboardOverride() &&
            line.permission.isNullOrBlank() &&
            !line.hasDisplayAnimationOverride()

    private fun serializeDetailedLine(line: HologramLine): Map<String, Any> {
        val lineMap = mutableMapOf<String, Any>(
            "type" to line.type.name,
            "content" to line.content,
            "offset.x" to line.offset.x,
            "offset.y" to line.offset.y,
            "offset.z" to line.offset.z,
            "scale.x" to line.scaleX,
            "scale.y" to line.scaleY,
            "scale.z" to line.scaleZ
        )
        if (line.hasHeightOverride()) lineMap["height"] = line.height
        if (line.hasBillboardOverride()) lineMap["billboard"] = line.billboard.name
        if (!line.permission.isNullOrBlank()) lineMap["permission"] = line.permission!!
        if (line.hasDisplayAnimationOverride()) lineMap["animation"] = line.displayAnimationOverride!!
        if (line is ItemLine && line.getItemAnimationFrames().isNotEmpty()) {
            lineMap["item-animation.frames"] = line.getItemAnimationFrames()
            lineMap["item-animation.frame-duration"] = line.getItemAnimationFrameDuration()
            lineMap["item-animation.loop"] = line.isItemAnimationLoop()
        }
        return lineMap
    }
}
