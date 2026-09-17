package org.axostudio.axohologram.feature.wand

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.core.hologram.line.LineType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * A two-corner editor for block-display holograms. The wand is tied to an ID
 * through PDC, so ordinary blaze rods never trigger its editor behavior.
 */
class HologramWandService(
    private val plugin: Plugin,
    private val hologramService: HologramService
) : Listener {

    private data class FirstCorner(val hologramId: String, val location: Location)

    private val hologramKey = NamespacedKey(plugin, "wand_hologram")
    private val firstCorners = ConcurrentHashMap<UUID, FirstCorner>()

    fun give(player: Player, hologram: Hologram): Boolean {
        if (!isBlockHologram(hologram)) return false

        val wand = ItemStack(Material.BLAZE_ROD)
        wand.itemMeta = wand.itemMeta.apply {
            displayName(MiniMessageUtil.parse("<light_purple><b>Varita de holograma</b></light_purple>"))
            lore(
                listOf(
                    MiniMessageUtil.parse("<gray>Holograma: <white>${hologram.id}</white>"),
                    MiniMessageUtil.parse("<gray>Izquierdo: primera esquina</gray>"),
                    MiniMessageUtil.parse("<gray>Derecho: segunda esquina y aplicar</gray>")
                )
            )
            persistentDataContainer.set(hologramKey, PersistentDataType.STRING, hologram.id)
        }
        player.inventory.addItem(wand).values.forEach { overflow ->
            player.world.dropItemNaturally(player.location, overflow)
        }
        return true
    }

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action !in setOf(Action.LEFT_CLICK_BLOCK, Action.RIGHT_CLICK_BLOCK)) return
        val id = event.item?.itemMeta?.persistentDataContainer?.get(hologramKey, PersistentDataType.STRING) ?: return
        val player = event.player
        if (!player.hasPermission("axohologram.wand") && !player.hasPermission("axohologram.admin")) return

        val hologram = hologramService.getHologram(id)
        if (hologram == null || !isBlockHologram(hologram)) {
            firstCorners.remove(player.uniqueId)
            player.sendMessage(MiniMessageUtil.parse("<red>Ese holograma de bloques ya no existe.</red>"))
            return
        }

        val clicked = event.clickedBlock?.location ?: return
        event.isCancelled = true

        if (event.action == Action.LEFT_CLICK_BLOCK) {
            firstCorners[player.uniqueId] = FirstCorner(hologram.id, clicked)
            player.sendMessage(MiniMessageUtil.parse("<yellow>Primera esquina de <white>${hologram.id}</white> marcada. Selecciona la segunda con clic derecho.</yellow>"))
            return
        }

        val first = firstCorners.remove(player.uniqueId)
        if (first == null || !first.hologramId.equals(hologram.id, ignoreCase = true)) {
            player.sendMessage(MiniMessageUtil.parse("<red>Marca primero la primera esquina con clic izquierdo.</red>"))
            return
        }
        if (first.location.world?.uid != clicked.world?.uid) {
            player.sendMessage(MiniMessageUtil.parse("<red>Las dos esquinas deben estar en el mismo mundo.</red>"))
            return
        }

        resizeToSelection(hologram, first.location, clicked)
        player.sendMessage(MiniMessageUtil.parse("<green><white>${hologram.id}</white> ahora cubre <yellow>${selectionSize(first.location, clicked)}</yellow>.</green>"))
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        firstCorners.remove(event.player.uniqueId)
    }

    private fun resizeToSelection(hologram: Hologram, first: Location, second: Location) {
        val world = requireNotNull(first.world)
        val minX = min(first.blockX, second.blockX)
        val minY = min(first.blockY, second.blockY)
        val minZ = min(first.blockZ, second.blockZ)
        val width = max(1, kotlin.math.abs(first.blockX - second.blockX) + 1)
        val height = max(1, kotlin.math.abs(first.blockY - second.blockY) + 1)
        val depth = max(1, kotlin.math.abs(first.blockZ - second.blockZ) + 1)
        val blockLine = hologram.pages.asSequence()
            .flatMap { it.lines.asSequence() }
            .first { it.type == LineType.BLOCK }

        val visibleAnchor = Location(
            world,
            minX + 0.5,
            minY + 0.5,
            minZ + 0.5,
            hologram.location?.yaw ?: 0f,
            hologram.location?.pitch ?: 0f
        )
        // Location is persisted before the hologram translation is applied by
        // the renderer, so compensate for an existing translation here.
        val baseLocation = visibleAnchor.clone().subtract(hologram.offset)
        hologram.setLocation(baseLocation, true)
        hologram.setScale(
            width / blockLine.scaleX.coerceAtLeast(0.01f),
            height / blockLine.scaleY.coerceAtLeast(0.01f),
            depth / blockLine.scaleZ.coerceAtLeast(0.01f)
        )
        hologramService.saveAll()
        hologramService.update(hologram)
    }

    private fun isBlockHologram(hologram: Hologram): Boolean = hologram.pages.any { page ->
        page.lines.any { it.type == LineType.BLOCK }
    }

    private fun selectionSize(first: Location, second: Location): String =
        "${kotlin.math.abs(first.blockX - second.blockX) + 1} × " +
            "${kotlin.math.abs(first.blockY - second.blockY) + 1} × " +
            "${kotlin.math.abs(first.blockZ - second.blockZ) + 1}"
}
