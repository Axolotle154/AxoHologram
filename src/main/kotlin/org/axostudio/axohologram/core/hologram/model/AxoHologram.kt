package org.axostudio.axohologram.core.hologram.model

import org.axostudio.axohologram.api.action.HologramAction
import org.axostudio.axohologram.api.action.HologramClickType
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.api.hologram.HologramPage
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.core.hologram.action.ActionRegistry
import org.axostudio.axohologram.core.hologram.line.LineManager
import org.axostudio.axohologram.core.hologram.visibility.VisibilityMode
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Vector
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AxoHologram(
    private val id: String,
    var position: HologramPosition,
    var settings: HologramSettings = HologramSettings(),
    pagesList: List<HologramPage> = emptyList(),
    val actionRegistry: ActionRegistry = ActionRegistry()
) : Hologram {

    internal var onLocationChanged: ((Boolean) -> Unit)? = null
    internal var onViewerShow: ((Player) -> Unit)? = null
    internal var onViewerHide: ((Player) -> Unit)? = null
    internal var onViewerUpdate: ((Player, Boolean) -> Unit)? = null
    internal var onDestroyRequested: (() -> Unit)? = null
    internal var onRefreshRequested: (() -> Unit)? = null
    internal var onCanView: ((Player) -> Boolean)? = null
    internal var onIsViewing: ((Player) -> Boolean)? = null
    internal var onActionsRequested: ((Player, HologramClickType) -> Unit)? = null

    private val pages: MutableList<HologramPage> = CopyOnWriteArrayList(pagesList)
    private var offset: Vector = Vector(0.0, 0.0, 0.0)
    private var heightOverride: Double? = null
    private val currentPages = ConcurrentHashMap<UUID, Int>()
    private var defaultPageIndex: Int = 0

    init {
        if (pages.isEmpty()) {
            pages.add(AxoHologramPage(0))
        }
    }

    override fun getId(): String = id
    override fun getWorldName(): String = position.worldName
    override fun isPersistent(): Boolean = settings.persistent
    override fun setPersistent(persistent: Boolean) {
        settings.persistent = persistent
    }

    override fun isEnabled(): Boolean = settings.enabled
    override fun setEnabled(enabled: Boolean) {
        settings.enabled = enabled
    }

    override fun getLocation(): Location? = position.toLocation()

    override fun setLocation(location: Location) {
        setLocation(location, true)
    }

    override fun setLocation(location: Location, persist: Boolean) {
        position = HologramPosition.fromLocation(location)
        onLocationChanged?.invoke(persist)
    }

    override fun getOffset(): Vector = offset.clone()
    override fun setOffset(offset: Vector) {
        this.offset = offset.clone()
    }

    override fun getPages(): List<HologramPage> = ArrayList(pages)

    override fun getPage(index: Int): HologramPage? {
        if (index in 0 until pages.size) return pages[index]
        return null
    }

    override fun addPage(page: HologramPage) {
        page.index = pages.size
        pages.add(page)
    }

    override fun removePage(index: Int) {
        if (index in 0 until pages.size) {
            pages.removeAt(index)
            if (pages.isEmpty()) pages.add(AxoHologramPage(0))
            for (i in pages.indices) {
                pages[i].index = i
            }
            defaultPageIndex = defaultPageIndex.coerceIn(0, pages.size - 1)
            currentPages.replaceAll { _, value -> value.coerceIn(0, pages.size - 1) }
        }
    }

    override fun pageCount(): Int = pages.size

    override fun addLine(line: String) {
        if (pages.isEmpty()) pages.add(AxoHologramPage(0))
        pages[0].addLine(line)
    }

    override fun addLine(line: HologramLine) {
        if (pages.isEmpty()) pages.add(AxoHologramPage(0))
        pages[0].addLine(line)
    }

    override fun addLines(lines: List<String>) {
        lines.forEach { addLine(it) }
    }

    override fun addLines(lines: Collection<HologramLine>) {
        lines.forEach { addLine(it) }
    }

    override fun addTextLine(line: String) {
        addLine(line)
    }

    override fun addTextLines(lines: Collection<String>) {
        lines.forEach { addLine(it) }
    }

    override fun getGroup(): String = settings.group
    override fun setGroup(group: String) {
        settings.group = group
    }

    override fun getPermission(): String? = settings.permission
    override fun setPermission(permission: String?) {
        settings.permission = permission
    }

    override fun getVisibilityMode(): VisibilityMode = settings.visibilityMode
    override fun setVisibilityMode(visibilityMode: VisibilityMode) {
        settings.visibilityMode = visibilityMode
    }

    override fun getViewDistance(): Int = settings.viewDistance
    override fun setViewDistance(viewDistance: Int) {
        settings.viewDistance = viewDistance
    }

    override fun getScale(): Float = maxOf(settings.scaleX, maxOf(settings.scaleY, settings.scaleZ))
    override fun setScale(scale: Float) {
        settings.scaleX = scale
        settings.scaleY = scale
        settings.scaleZ = scale
    }

    override fun getScaleX(): Float = settings.scaleX
    override fun getScaleY(): Float = settings.scaleY
    override fun getScaleZ(): Float = settings.scaleZ
    override fun setScale(scaleX: Float, scaleY: Float, scaleZ: Float) {
        settings.scaleX = scaleX
        settings.scaleY = scaleY
        settings.scaleZ = scaleZ
    }

    override fun getHeight(): Double = heightOverride ?: settings.lineHeight
    override fun setHeight(height: Double) {
        this.heightOverride = height
    }
    override fun clearHeight() {
        this.heightOverride = null
    }
    override fun hasHeightOverride(): Boolean = heightOverride != null

    override fun getBillboard(): Display.Billboard = settings.billboard
    override fun setBillboard(billboard: Display.Billboard) {
        settings.billboard = billboard
    }

    override fun getShadowStrength(): Float = settings.shadowStrength
    override fun setShadowStrength(shadowStrength: Float) {
        settings.shadowStrength = shadowStrength
    }

    override fun getShadowRadius(): Float = settings.shadowRadius
    override fun setShadowRadius(shadowRadius: Float) {
        settings.shadowRadius = shadowRadius
    }

    override fun getBrightnessBlock(): Int = settings.brightnessBlock
    override fun setBrightnessBlock(blockBrightness: Int) {
        settings.brightnessBlock = blockBrightness
    }

    override fun getBrightnessSky(): Int = settings.brightnessSky
    override fun setBrightnessSky(skyBrightness: Int) {
        settings.brightnessSky = skyBrightness
    }

    override fun getBackgroundColor(): Color = settings.backgroundColor
    override fun setBackgroundColor(backgroundColor: Color) {
        settings.backgroundColor = backgroundColor
    }

    override fun hasTextShadow(): Boolean = settings.textShadow
    override fun setTextShadow(textShadow: Boolean) {
        settings.textShadow = textShadow
    }

    override fun isSeeThrough(): Boolean = settings.seeThrough
    override fun setSeeThrough(seeThrough: Boolean) {
        settings.seeThrough = seeThrough
    }

    override fun getAlignment(): TextDisplay.TextAlignment = settings.alignment
    override fun setAlignment(alignment: TextDisplay.TextAlignment) {
        settings.alignment = alignment
    }

    override fun getUpdateTextInterval(): Long = settings.updateTextInterval
    override fun setUpdateTextInterval(updateTextInterval: Long) {
        settings.updateTextInterval = updateTextInterval
    }

    override fun getDisplayAnimation(): String? = settings.displayAnimation
    override fun setDisplayAnimation(displayAnimation: String?) {
        settings.displayAnimation = displayAnimation
    }

    override fun isDisplayAnimationEnabled(): Boolean = settings.displayAnimationEnabled
    override fun setDisplayAnimationEnabled(enabled: Boolean) {
        settings.displayAnimationEnabled = enabled
    }

    override fun getEffectivePermission(): String? {
        val perm = settings.permission
        if (!perm.isNullOrBlank()) return perm
        val grp = settings.group
        if (grp.isNotBlank()) return "axohologram.group.$grp"
        return null
    }

    override fun getLinkedNpc(): String? = settings.linkedNpc
    override fun setLinkedNpc(linkedNpc: String?) {
        settings.linkedNpc = linkedNpc
    }

    override fun getActions(clickType: HologramClickType): List<HologramAction> {
        return actionRegistry.getActions(clickType)
    }

    override fun addAction(clickType: HologramClickType, action: HologramAction) {
        actionRegistry.addAction(clickType, action)
    }

    override fun removeAction(clickType: HologramClickType, index: Int): HologramAction? {
        return actionRegistry.removeAction(clickType, index)
    }

    override fun executeActions(player: Player, clickType: HologramClickType) {
        onActionsRequested?.invoke(player, clickType)
    }

    override fun setCurrentPage(player: Player, pageIndex: Int): Boolean {
        if (pageIndex !in pages.indices) return false
        currentPages[player.uniqueId] = pageIndex
        return true
    }

    override fun getDefaultPageIndex(): Int = defaultPageIndex

    override fun setDefaultPageIndex(index: Int) {
        if (index in pages.indices) defaultPageIndex = index
    }

    override fun getCurrentPage(player: Player): Int =
        currentPages[player.uniqueId]?.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            ?: defaultPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    override fun changePage(player: Player, delta: Int): Boolean {
        if (pages.isEmpty() || delta == 0) return false
        val current = getCurrentPage(player)
        val target = (current + delta).coerceIn(0, pages.size - 1)
        if (target == current) return false
        currentPages[player.uniqueId] = target
        return true
    }

    override fun show(player: Player) {
        onViewerShow?.invoke(player)
    }

    override fun hide(player: Player) {
        onViewerHide?.invoke(player)
    }

    override fun update(player: Player) {
        onViewerUpdate?.invoke(player, true)
    }

    override fun destroy() {
        onDestroyRequested?.invoke()
    }

    override fun refreshViewers() {
        onRefreshRequested?.invoke()
    }

    override fun requiresPeriodicRefresh(): Boolean = pages.any { page ->
        page.lines.any { line -> MiniMessageUtil.containsPlaceholderApiToken(line.content) }
    }

    override fun updateVisibility(player: Player, force: Boolean) {
        onViewerUpdate?.invoke(player, force)
    }

    override fun isViewing(player: Player): Boolean = onIsViewing?.invoke(player) ?: false

    override fun canView(player: Player): Boolean = onCanView?.invoke(player) ?: false

    override fun clone(): AxoHologram {
        return cloneWithId(id)
    }

    fun cloneWithId(newId: String): AxoHologram {
        val clonedPages = pages.map { it.clone() }
        val cloned = AxoHologram(
            id = newId,
            position = position.clone(),
            settings = settings.clone(),
            pagesList = clonedPages,
            actionRegistry = actionRegistry.clone()
        )
        cloned.setOffset(offset)
        if (heightOverride != null) cloned.setHeight(heightOverride!!)
        cloned.defaultPageIndex = defaultPageIndex.coerceIn(0, (cloned.pages.size - 1).coerceAtLeast(0))
        return cloned
    }
}
