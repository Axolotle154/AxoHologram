package org.axostudio.axohologram.listener;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.action.HologramClickType;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class HologramInteractionListener implements Listener {

    private final AxoHologram plugin;

    public HologramInteractionListener(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        HologramPacketManager.TrackedDisplay trackedDisplay = HologramPacketManager.getTrackedDisplay(event.getRightClicked().getUniqueId());
        if (!isValidInteraction(event.getPlayer(), trackedDisplay)) {
            return;
        }

        event.setCancelled(true);
        Hologram hologram = plugin.getHologramManager().getHologram(trackedDisplay.hologramId());
        if (hologram != null) {
            hologram.executeActions(event.getPlayer(), HologramClickType.RIGHT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        HologramPacketManager.TrackedDisplay trackedDisplay = HologramPacketManager.getTrackedDisplay(event.getEntity().getUniqueId());
        if (!isValidInteraction(player, trackedDisplay)) {
            return;
        }

        event.setCancelled(true);
        Hologram hologram = plugin.getHologramManager().getHologram(trackedDisplay.hologramId());
        if (hologram != null) {
            hologram.executeActions(player, HologramClickType.LEFT);
        }
    }

    private boolean isValidInteraction(Player player, HologramPacketManager.TrackedDisplay trackedDisplay) {
        return trackedDisplay != null
                && trackedDisplay.viewerId().equals(player.getUniqueId());
    }
}
