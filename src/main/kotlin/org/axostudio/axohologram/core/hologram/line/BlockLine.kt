package org.axostudio.axohologram.core.hologram.line

import org.axostudio.axohologram.api.hologram.HologramLine
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.util.Vector

class BlockLine(
    private var content: String = "STONE",
    private var offset: Vector = Vector(0.0, 0.0, 0.0),
    private var heightOverride: Double? = null,
    private var billboardOverride: Display.Billboard? = null,
    private var permission: String? = null,
    private var displayAnimationOverride: String? = null,
    private var scaleX: Float = 1.0f,
    private var scaleY: Float = 1.0f,
    private var scaleZ: Float = 1.0f
) : HologramLine {

    private var cachedBlockData: BlockData? = null

    fun getBlockData(): BlockData {
        val cached = cachedBlockData
        if (cached != null) return cached

        val parsed = try {
            Bukkit.createBlockData(content)
        } catch (e: Exception) {
            val mat = Material.matchMaterial(content.trim().uppercase()) ?: Material.STONE
            if (mat.isBlock) mat.createBlockData() else Material.STONE.createBlockData()
        }
        cachedBlockData = parsed
        return parsed
    }

    fun setBlockData(blockData: BlockData) {
        this.cachedBlockData = blockData
        this.content = blockData.asString
    }

    override fun getType(): LineType = LineType.BLOCK

    override fun getContent(): String = content
    override fun setContent(content: String) {
        this.content = content
        this.cachedBlockData = null
    }

    override fun getOffset(): Vector = offset.clone()
    override fun setOffset(offset: Vector) {
        this.offset = offset.clone()
    }

    override fun getHeight(): Double = heightOverride ?: 0.5
    override fun setHeight(height: Double) {
        this.heightOverride = height
    }
    override fun clearHeight() {
        this.heightOverride = null
    }
    override fun hasHeightOverride(): Boolean = heightOverride != null

    override fun getBillboard(): Display.Billboard = billboardOverride ?: Display.Billboard.CENTER
    override fun setBillboard(billboard: Display.Billboard) {
        this.billboardOverride = billboard
    }
    override fun hasBillboardOverride(): Boolean = billboardOverride != null

    override fun getPermission(): String? = permission
    override fun setPermission(permission: String?) {
        this.permission = permission
    }

    override fun canView(player: Player?): Boolean {
        if (player == null) return false
        val perm = permission ?: return true
        return perm.isEmpty() || player.hasPermission(perm)
    }

    override fun hasDisplayAnimationOverride(): Boolean = !displayAnimationOverride.isNullOrBlank()
    override fun getDisplayAnimationOverride(): String? = displayAnimationOverride
    override fun setDisplayAnimationOverride(animationName: String?) {
        this.displayAnimationOverride = animationName
    }

    override fun getScaleX(): Float = scaleX
    override fun getScaleY(): Float = scaleY
    override fun getScaleZ(): Float = scaleZ

    override fun setScale(scale: Float) {
        this.scaleX = scale
        this.scaleY = scale
        this.scaleZ = scale
    }

    override fun setScale(scaleX: Float, scaleY: Float, scaleZ: Float) {
        this.scaleX = scaleX
        this.scaleY = scaleY
        this.scaleZ = scaleZ
    }
}
