package org.axostudio.axohologram.menu.hologram

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.core.hologram.visibility.VisibilityMode
import org.axostudio.axohologram.menu.MenuRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Display
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class HologramEditorMenu(
    private val registry: MenuRegistry,
    private val hologramService: HologramService
) {

    fun open(player: Player, hologram: Hologram) {
        val inv = Bukkit.createInventory(player, 27, MiniMessageUtil.parse("<gold>Edit: <yellow>${hologram.id}</yellow></gold>"))

        // Item 10: Toggle shadow
        val shadowItem = ItemStack(if (hologram.hasTextShadow()) Material.TORCH else Material.SOUL_TORCH)
        val shadowMeta = shadowItem.itemMeta
        shadowMeta?.displayName(MiniMessageUtil.parse("<yellow>Text Shadow: <gold>${hologram.hasTextShadow()}</gold></yellow>"))
        shadowItem.itemMeta = shadowMeta
        inv.setItem(10, shadowItem)

        // Item 12: Visibility Mode
        val visItem = ItemStack(Material.ENDER_EYE)
        val visMeta = visItem.itemMeta
        visMeta?.displayName(MiniMessageUtil.parse("<yellow>Visibility: <gold>${hologram.visibilityMode.name}</gold></yellow>"))
        visItem.itemMeta = visMeta
        inv.setItem(12, visItem)

        // Item 14: Billboard
        val billItem = ItemStack(Material.ITEM_FRAME)
        val billMeta = billItem.itemMeta
        billMeta?.displayName(MiniMessageUtil.parse("<yellow>Billboard: <gold>${hologram.billboard.name}</gold></yellow>"))
        billItem.itemMeta = billMeta
        inv.setItem(14, billItem)

        // Item 16: Teleport to player
        val tpItem = ItemStack(Material.COMPASS)
        val tpMeta = tpItem.itemMeta
        tpMeta?.displayName(MiniMessageUtil.parse("<green>Move to your location</green>"))
        tpItem.itemMeta = tpMeta
        inv.setItem(16, tpItem)

        player.openInventory(inv)

        registry.registerOpenMenu(player) { event ->
            if (event.rawSlot !in 0 until event.view.topInventory.size) return@registerOpenMenu

            when (event.rawSlot) {
                10 -> {
                    hologram.setTextShadow(!hologram.hasTextShadow())
                    saveAndRefresh(hologram)
                    open(player, hologram)
                }
                12 -> {
                    val modes = VisibilityMode.values()
                    val nextMode = modes[(modes.indexOf(hologram.visibilityMode) + 1) % modes.size]
                    hologram.visibilityMode = nextMode
                    saveAndRefresh(hologram)
                    open(player, hologram)
                }
                14 -> {
                    val billboards = Display.Billboard.values()
                    val nextBillboard = billboards[(billboards.indexOf(hologram.billboard) + 1) % billboards.size]
                    hologram.billboard = nextBillboard
                    saveAndRefresh(hologram)
                    open(player, hologram)
                }
                16 -> {
                    hologram.setLocation(player.location, true)
                    player.sendMessage(MiniMessageUtil.parse("<green>Hologram moved to your location!</green>"))
                    player.closeInventory()
                }
            }
        }
    }

    private fun saveAndRefresh(hologram: Hologram) {
        hologramService.update(hologram)
        if (hologram.isPersistent) hologramService.saveAll()
    }
}
