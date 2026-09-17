package org.axostudio.axohologram.menu.media

import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.feature.media.MediaHologram
import org.axostudio.axohologram.feature.media.MediaManager
import org.axostudio.axohologram.feature.media.menu.MediaMenu
import org.axostudio.axohologram.menu.MenuRegistry
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class MediaMenuImpl(
    private val menuRegistry: MenuRegistry,
    private val mediaManager: MediaManager
) : MediaMenu {

    override fun open(player: Player, media: MediaHologram) {
        val inv = Bukkit.createInventory(player, 27, MiniMessageUtil.parse("<gold>Media: <yellow>${media.id}</yellow></gold>"))

        val playPauseItem = ItemStack(if (media.isPlaying) Material.REDSTONE_BLOCK else Material.EMERALD_BLOCK)
        val meta = playPauseItem.itemMeta
        meta?.displayName(MiniMessageUtil.parse(if (media.isPlaying) "<red>Pause</red>" else "<green>Play</green>"))
        playPauseItem.itemMeta = meta
        inv.setItem(13, playPauseItem)

        player.openInventory(inv)

        menuRegistry.registerOpenMenu(player) { event ->
            if (event.rawSlot == 13) {
                if (media.isPlaying) mediaManager.pause(media.id) else mediaManager.play(media.id)
                open(player, media)
            }
        }
    }
}
