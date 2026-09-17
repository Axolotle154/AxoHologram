package org.axostudio.axohologram.menu.hologram

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.menu.MenuRegistry
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class HologramListMenu(
    private val hologramService: HologramService,
    private val registry: MenuRegistry,
    private val onSelect: (Player, String) -> Unit
) {
    fun open(player: Player, page: Int = 0) {
        val holograms: List<Hologram> = hologramService.getAll().toList()
        val inv = Bukkit.createInventory(player, 54, MiniMessageUtil.parse("<gold>Holograms (${holograms.size})</gold>"))

        val pageSize = 45
        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, holograms.size)

        for (i in startIndex until endIndex) {
            val holo = holograms[i]
            val item = ItemStack(Material.ARMOR_STAND)
            val meta = item.itemMeta
            meta?.displayName(MiniMessageUtil.parse("<yellow>${holo.id}</yellow>"))
            val lore = listOf(
                MiniMessageUtil.parse("<gray>Pages: <white>${holo.pageCount()}</white></gray>"),
                MiniMessageUtil.parse("<gray>Lines: <white>${holo.pages.firstOrNull()?.lineCount() ?: 0}</white></gray>"),
                MiniMessageUtil.parse("<gray>Click to edit</gray>")
            )
            meta?.lore(lore)
            item.itemMeta = meta
            inv.setItem(i - startIndex, item)
        }

        player.openInventory(inv)

        registry.registerOpenMenu(player) { event ->
            val slot = event.rawSlot
            if (slot in 0 until (endIndex - startIndex)) {
                val holo = holograms[startIndex + slot]
                onSelect(player, holo.id)
            }
        }
    }
}
