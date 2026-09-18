package org.axostudio.axohologram.api.event;

import org.axostudio.axohologram.api.action.HologramClickType;
import org.axostudio.axohologram.api.hologram.Hologram;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class HologramClickEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Hologram hologram;
    private final HologramClickType clickType;
    private final int pageIndex;
    private final int lineIndex;
    private boolean cancelled;

    public HologramClickEvent(
            @NotNull Player player,
            @NotNull Hologram hologram,
            @NotNull HologramClickType clickType,
            int pageIndex,
            int lineIndex
    ) {
        this.player = player;
        this.hologram = hologram;
        this.clickType = clickType;
        this.pageIndex = pageIndex;
        this.lineIndex = lineIndex;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public Hologram getHologram() {
        return hologram;
    }

    @NotNull
    public HologramClickType getClickType() {
        return clickType;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getLineIndex() {
        return lineIndex;
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
