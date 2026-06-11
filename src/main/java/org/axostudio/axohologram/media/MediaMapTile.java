package org.axostudio.axohologram.media;

import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

public final class MediaMapTile {

    private final int column;
    private final int row;
    private final int columns;
    private final int rows;
    private final MapView mapView;
    private final MapFrameRenderer renderer;
    private final ItemStack mapItem;
    private volatile ItemFrame display;

    public MediaMapTile(int column, int row, int columns, int rows, MapView mapView, MapFrameRenderer renderer, ItemStack mapItem) {
        this.column = column;
        this.row = row;
        this.columns = columns;
        this.rows = rows;
        this.mapView = mapView;
        this.renderer = renderer;
        this.mapItem = mapItem == null ? null : mapItem.clone();
    }

    public int column() {
        return column;
    }

    public int row() {
        return row;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public MapView mapView() {
        return mapView;
    }

    public MapFrameRenderer renderer() {
        return renderer;
    }

    public ItemStack mapItem() {
        return mapItem == null ? null : mapItem.clone();
    }

    public ItemFrame display() {
        return display;
    }

    public void display(ItemFrame display) {
        this.display = display;
    }
}
