package org.axostudio.axohologram.core.hologram.visibility

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.common.math.VectorUtil
import org.bukkit.entity.Player

object VisibilityRule {

    @JvmStatic
    fun canPlayerSee(player: Player?, hologram: Hologram?): Boolean {
        if (player == null || !player.isOnline || hologram == null || !hologram.isEnabled) {
            return false
        }

        val holoLoc = hologram.location ?: return false
        val pLoc = player.location

        if (holoLoc.world == null || pLoc.world != holoLoc.world) {
            return false
        }

        val maxDist = hologram.viewDistance.toDouble()
        if (VectorUtil.distanceSquared(pLoc, holoLoc) > maxDist * maxDist) {
            return false
        }

        val perm = hologram.effectivePermission
        if (!perm.isNullOrEmpty() && !player.hasPermission(perm)) {
            return false
        }

        return when (hologram.visibilityMode) {
            VisibilityMode.ALL -> true
            VisibilityMode.PERMISSION -> perm.isNullOrEmpty() || player.hasPermission(perm)
            VisibilityMode.RADIUS -> true
            VisibilityMode.NONE -> false
            VisibilityMode.MANUAL, VisibilityMode.CONDITION, VisibilityMode.SCRIPT -> false
        }
    }
}
