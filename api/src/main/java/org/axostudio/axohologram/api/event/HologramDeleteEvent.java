package org.axostudio.axohologram.api.event;

import org.axostudio.axohologram.api.hologram.Hologram;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class HologramDeleteEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Hologram hologram;
    private boolean cancelled;

    public HologramDeleteEvent(@NotNull Hologram hologram) {
        this.hologram = hologram;
    }

    @NotNull
    public Hologram getHologram() {
        return hologram;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
