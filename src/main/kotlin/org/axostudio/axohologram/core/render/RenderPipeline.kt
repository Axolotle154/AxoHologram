package org.axostudio.axohologram.core.render

import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.core.hologram.line.BlockLine
import org.axostudio.axohologram.core.hologram.line.ItemLine
import org.axostudio.axohologram.core.hologram.line.LineType
import org.axostudio.axohologram.core.hologram.line.TextLine
import org.axostudio.axohologram.platform.packet.HologramPacketManager
import org.axostudio.axohologram.feature.animation.AnimationEngine
import org.axostudio.axohologram.feature.animation.display.DisplayAnimationRenderer
import org.bukkit.Bukkit
import org.bukkit.entity.Display
import org.bukkit.Location

class RenderPipeline {
    var animationEngine: AnimationEngine? = null
    var currentTick: () -> Long = { 0L }

    fun renderLine(
        context: RenderContext,
        lineIndex: Int,
        line: HologramLine,
        lineYOffset: Double,
        isUpdate: Boolean = false
    ) {
        if (!line.canView(context.player)) {
            HologramPacketManager.destroyLine(
                context.player.uniqueId,
                context.hologram.id,
                context.pageIndex,
                lineIndex
            )
            return
        }

        val baseLoc = context.baseLocation
        val lineOffset = line.offset
        val targetLoc = Location(
            baseLoc.world,
            baseLoc.x + lineOffset.x,
            baseLoc.y + lineOffset.y + lineYOffset,
            baseLoc.z + lineOffset.z,
            baseLoc.yaw,
            baseLoc.pitch
        )

        val billboard = if (line.hasBillboardOverride()) line.billboard else context.hologram.billboard

        when (line.type) {
            LineType.TEXT, LineType.ANIMATED_TEXT, LineType.COMPOSITE -> {
                val animatedContent = animationEngine?.processText(line.content, currentTick()) ?: line.content
                val component = MiniMessageUtil.parse(animatedContent, context.player)
                if (isUpdate) {
                    HologramPacketManager.updateTextLine(
                        context.player,
                        context.hologram,
                        context.pageIndex,
                        lineIndex,
                        targetLoc,
                        component,
                        billboard,
                        line
                    )
                } else {
                    HologramPacketManager.spawnTextLine(
                        context.player,
                        context.hologram,
                        context.pageIndex,
                        lineIndex,
                        targetLoc,
                        component,
                        billboard,
                        line
                    )
                }
            }
            LineType.ITEM -> {
                val itemLine = line as? ItemLine
                val itemStack = itemLine?.getItemStack(currentTick())
                if (isUpdate) {
                    HologramPacketManager.updateItemLine(
                        context.player,
                        context.hologram,
                        context.pageIndex,
                        lineIndex,
                        targetLoc,
                        itemStack,
                        billboard,
                        line
                    )
                } else {
                    HologramPacketManager.spawnItemLine(
                        context.player,
                        context.hologram,
                        context.pageIndex,
                        lineIndex,
                        targetLoc,
                        itemStack,
                        billboard,
                        line
                    )
                }
            }
            LineType.BLOCK -> {
                val blockLine = line as? BlockLine
                val blockData = blockLine?.getBlockData()
                if (isUpdate) {
                    HologramPacketManager.updateBlockLine(
                        context.player,
                        context.hologram,
                        context.pageIndex,
                        lineIndex,
                        targetLoc,
                        blockData,
                        billboard,
                        line
                    )
                } else {
                    HologramPacketManager.spawnBlockLine(
                        context.player,
                        context.hologram,
                        context.pageIndex,
                        lineIndex,
                        targetLoc,
                        blockData,
                        billboard,
                        line
                    )
                }
            }
            else -> {}
        }

        applyDisplayAnimation(context, lineIndex, line)
    }

    private fun applyDisplayAnimation(context: RenderContext, lineIndex: Int, line: HologramLine) {
        val engine = animationEngine ?: return
        val animationName = line.displayAnimationOverride
            ?: context.hologram.displayAnimation.takeIf { context.hologram.isDisplayAnimationEnabled }
            ?: return
        val animation = engine.registry.getDisplayAnimation(animationName) ?: return
        val entityId = HologramPacketManager.tracker()
            .getPlayerEntities(context.player.uniqueId)[
                org.axostudio.axohologram.platform.packet.PacketViewerTracker.LineKey(context.hologram.id, context.pageIndex, lineIndex)
            ] ?: return
        val display = Bukkit.getEntity(entityId) as? Display ?: return
        if (!display.isValid) return
        DisplayAnimationRenderer.applyFrame(display, context.baseLocation, animation.getFrame(currentTick()), animation.interpolationDuration)
    }
}
