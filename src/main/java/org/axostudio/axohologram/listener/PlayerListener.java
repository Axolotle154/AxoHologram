package org.axostudio.axohologram.listener;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final AxoHologram plugin;

    public PlayerListener(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
            for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
                hologram.updateVisibility(player);
            }
        }, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            if (hologram.isViewing(player)) {
                hologram.hide(player);
            }
        }
        // Clean up any remaining packet data for the player
        HologramPacketManager.destroyAllHologramLinesForPlayer(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            hologram.updateVisibility(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
            for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
                hologram.updateVisibility(player);
            }
        }, 5L);
    }
}
