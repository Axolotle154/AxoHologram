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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class HologramInteractionListener implements Listener {

    private final AxoHologram plugin;

    public HologramInteractionListener(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (isMediaDisplay(event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }

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
            handleClick(event.getPlayer(), hologram, HologramClickType.RIGHT, -1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isMediaDisplay(event.getEntity())) {
            event.setCancelled(true);
            return;
        }

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
            handleClick(player, hologram, HologramClickType.LEFT, 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (isMediaDisplay(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakEvent event) {
        if (isMediaDisplay(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void handleClick(Player player, Hologram hologram, HologramClickType clickType, int defaultPageDelta) {
        if (hasConfiguredClickActions(hologram, clickType)) {
            hologram.executeActions(player, clickType);
            return;
        }

        if (hologram.getPages().size() > 1) {
            hologram.changePage(player, defaultPageDelta);
        }
    }

    private boolean hasConfiguredClickActions(Hologram hologram, HologramClickType clickType) {
        return !hologram.getActions(HologramClickType.ANY).isEmpty()
                || !hologram.getActions(clickType).isEmpty();
    }

    private boolean isValidInteraction(Player player, HologramPacketManager.TrackedDisplay trackedDisplay) {
        return trackedDisplay != null
                && trackedDisplay.viewerId().equals(player.getUniqueId());
    }

    private boolean isMediaDisplay(org.bukkit.entity.Entity entity) {
        return plugin.getMediaManager() != null && plugin.getMediaManager().isMediaDisplay(entity);
    }
}
