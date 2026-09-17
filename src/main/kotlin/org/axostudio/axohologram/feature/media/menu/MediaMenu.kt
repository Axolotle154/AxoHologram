package org.axostudio.axohologram.feature.media.menu

import org.axostudio.axohologram.feature.media.MediaHologram
import org.bukkit.entity.Player

interface MediaMenu {
    fun open(player: Player, media: MediaHologram)
}
