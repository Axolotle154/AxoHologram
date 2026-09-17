package org.axostudio.axohologram.menu.animation

import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.feature.animation.AnimationRegistry
import org.axostudio.axohologram.menu.MenuRegistry
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class AnimationMenu(
    private val registry: AnimationRegistry,
    private val menuRegistry: MenuRegistry
) {
    fun open(player: Player) {
        val anims = registry.getAllDisplayAnimations().toList()
        val inv = Bukkit.createInventory(player, 36, MiniMessageUtil.parse("<gold>Animations (${anims.size})</gold>"))

        for ((index, anim) in anims.withIndex()) {
            if (index >= 36) break
            val item = ItemStack(Material.FIREWORK_STAR)
            val meta = item.itemMeta
            meta?.displayName(MiniMessageUtil.parse("<yellow>${anim.name}</yellow>"))
            item.itemMeta = meta
            inv.setItem(index, item)
        }

        player.openInventory(inv)
    }
}
