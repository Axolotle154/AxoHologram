package org.axostudio.axohologram.core.render

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.core.hologram.line.LineManager
import org.axostudio.axohologram.core.hologram.line.TextLine
import org.axostudio.axohologram.core.hologram.page.PageController
import org.axostudio.axohologram.platform.packet.HologramPacketManager
import org.axostudio.axohologram.feature.animation.AnimationEngine
import org.bukkit.entity.Player

class HologramRenderer(
    private val pageController: PageController,
    private val lineHeight: (Hologram, HologramLine) -> Double = { hologram, line ->
        if (line.hasHeightOverride()) line.height else hologram.height
    },
    private val pipeline: RenderPipeline = RenderPipeline()
) {
    fun configureAnimations(engine: AnimationEngine, currentTick: () -> Long) {
        pipeline.animationEngine = engine
        pipeline.currentTick = currentTick
    }

    fun render(player: Player, hologram: Hologram, isUpdate: Boolean = false) {
        val baseLoc = hologram.location ?: return
        val pageIndex = hologram.getCurrentPage(player)
        val page = hologram.getPage(pageIndex) ?: hologram.getPage(0) ?: return

        // Page changes and line deletions must remove entities that are no longer
        // represented by the current render pass.
        val lines = page.lines
        // `translation` was already part of the persisted schema in the
        // previous plugin.  It is an offset from the saved location, not a
        // display transformation, so it must be included before rendering.
        val translatedBase = baseLoc.clone().add(hologram.offset)

        HologramPacketManager.destroyOtherPages(player, hologram.id, page.index)
        if (isLegacySimpleTextPage(lines)) {
            renderLegacySimpleTextPage(player, hologram, page.index, lines, translatedBase, isUpdate)
            return
        }
        HologramPacketManager.destroyLinesExcept(player, hologram.id, page.index, page.lines.indices.toSet())

        val yOffsets = LineManager.calculateLineYOffsets(lines, player) { line -> lineHeight(hologram, line) }

        val context = RenderContext(
            player = player,
            hologram = hologram,
            pageIndex = page.index,
            baseLocation = translatedBase
        )

        for (i in lines.indices) {
            val line = lines[i]
            val yOffset = yOffsets.getOrElse(i) { 0.0 }
            pipeline.renderLine(context, i, line, yOffset, isUpdate)
        }
    }

    fun despawn(player: Player, hologram: Hologram) {
        val viewerId = player.uniqueId
        for (page in hologram.pages) {
            for (i in 0 until page.lineCount()) {
                HologramPacketManager.destroyLine(viewerId, hologram.id, page.index, i)
            }
        }
    }

    fun despawnAll(hologram: Hologram) {
        HologramPacketManager.destroyAllForHologram(hologram.id)
    }

    /**
     * The legacy implementation rendered an ordinary page of text as one
     * multi-line TextDisplay.  A TextDisplay anchors multi-line text
     * differently from several individual entities, so preserving this is
     * essential for old files to retain their visible Y position.
     */
    private fun isLegacySimpleTextPage(lines: List<HologramLine>): Boolean =
        lines.isNotEmpty() && lines.all { line ->
            line is TextLine &&
                !line.hasBillboardOverride() &&
                !line.hasDisplayAnimationOverride() &&
                !line.hasHeightOverride() &&
                line.permission.isNullOrBlank() &&
                line.offset.lengthSquared() == 0.0 &&
                line.scaleX == 1.0f && line.scaleY == 1.0f && line.scaleZ == 1.0f
        }

    private fun renderLegacySimpleTextPage(
        player: Player,
        hologram: Hologram,
        pageIndex: Int,
        lines: List<HologramLine>,
        baseLocation: org.bukkit.Location,
        isUpdate: Boolean
    ) {
        HologramPacketManager.destroyLinesExcept(player, hologram.id, pageIndex, setOf(-1))
        val text = Component.join(
            JoinConfiguration.newlines(),
            lines.map { line -> MiniMessageUtil.parse(line.content, player) }
        )
        val representativeLine = lines.first()
        if (isUpdate) {
            HologramPacketManager.updateTextLine(
                player, hologram, pageIndex, -1, baseLocation, text, hologram.billboard, representativeLine
            )
        } else {
            HologramPacketManager.spawnTextLine(
                player, hologram, pageIndex, -1, baseLocation, text, hologram.billboard, representativeLine
            )
        }
    }
}
