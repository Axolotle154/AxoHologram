package org.axostudio.axohologram.listener;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.axostudio.axohologram.util.MiniMessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PlayerListener implements Listener {

    private static final long[] JOIN_VISIBILITY_DELAYS = {5L, 20L, 60L};
    private static final long[] TRANSITION_VISIBILITY_DELAYS = {1L, 10L, 40L};

    private final AxoHologram plugin;

    public PlayerListener(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        scheduleVisibilityRefreshes(player, JOIN_VISIBILITY_DELAYS);
        plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
            if (plugin.getUpdateChecker() != null) {
                plugin.getUpdateChecker().notifyPlayer(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            if (hologram.isViewing(player)) {
                hologram.hide(player);
            }
        }
        MiniMessageUtil.clearPlaceholderApiCache(player.getUniqueId());
        plugin.getHologramManager().clearVisibilityState(player.getUniqueId());
        // Clean up any remaining packet data for the player
        HologramPacketManager.destroyAllHologramLinesForPlayer(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        scheduleVisibilityRefreshes(player, TRANSITION_VISIBILITY_DELAYS);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        scheduleVisibilityRefreshes(player, JOIN_VISIBILITY_DELAYS);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }

        scheduleVisibilityRefreshes(event.getPlayer(), TRANSITION_VISIBILITY_DELAYS);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getWorld() == event.getTo().getWorld()
                && Double.compare(event.getFrom().getX(), event.getTo().getX()) == 0
                && Double.compare(event.getFrom().getY(), event.getTo().getY()) == 0
                && Double.compare(event.getFrom().getZ(), event.getTo().getZ()) == 0) {
            return;
        }

        plugin.getHologramManager().handlePlayerMovement(event.getPlayer(), event.getTo());
    }

    private void scheduleVisibilityRefreshes(Player player, long... delays) {
        for (long delay : delays) {
            plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
                plugin.getHologramManager().updateVisibilityForPlayer(player, true);
            }, delay);
        }
    }
}
