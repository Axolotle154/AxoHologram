package org.axostudio.axohologram.menu

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.feature.animation.AnimationRegistry
import org.axostudio.axohologram.feature.media.MediaManager
import org.axostudio.axohologram.menu.media.MediaMenuImpl
import org.axostudio.axohologram.menu.animation.AnimationMenu
import org.axostudio.axohologram.menu.hologram.HologramEditorMenu
import org.axostudio.axohologram.menu.hologram.HologramListMenu
import org.bukkit.entity.Player

class MenuManager(
    val registry: MenuRegistry = MenuRegistry(),
    private val hologramService: HologramService,
    private val animationRegistry: AnimationRegistry,
    private val mediaManager: MediaManager? = null
) {
    val editorMenu = HologramEditorMenu(registry, hologramService)
    val listMenu = HologramListMenu(hologramService, registry) { player, holoId ->
        val holo = hologramService.get(holoId).orElse(null)
        if (holo != null) {
            editorMenu.open(player, holo)
        }
    }
    val animationMenu = AnimationMenu(animationRegistry, registry)
    private val mediaMenu = mediaManager?.let { MediaMenuImpl(registry, it) }

    fun openListMenu(player: Player, page: Int = 0) {
        listMenu.open(player, page)
    }

    fun openEditor(player: Player, hologram: Hologram) {
        editorMenu.open(player, hologram)
    }

    fun openAnimations(player: Player) {
        animationMenu.open(player)
    }

    fun openMedia(player: Player, mediaId: String): Boolean {
        val media = mediaManager?.get(mediaId) ?: return false
        mediaMenu?.open(player, media)
        return true
    }
}
