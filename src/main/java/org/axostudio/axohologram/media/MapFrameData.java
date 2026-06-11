package org.axostudio.axohologram.media;

import org.bukkit.map.MapPalette;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public record MapFrameData(
        int width,
        int height,
        int columns,
        int rows,
        List<byte[]> tiles
) {

    private static final int MAP_SIZE = 128;

    public MapFrameData {
        tiles = tiles == null ? List.of() : List.copyOf(tiles);
    }

    public byte[] tile(int column, int row) {
        int index = row * columns + column;
        return tiles.get(index);
    }

    public int tileCount() {
        return tiles.size();
    }

    @SuppressWarnings("deprecation")
    public static MapFrameData fromImage(BufferedImage image) {
        int columns = Math.max(1, (int) Math.ceil((double) image.getWidth() / MAP_SIZE));
        int rows = Math.max(1, (int) Math.ceil((double) image.getHeight() / MAP_SIZE));
        List<byte[]> tiles = new ArrayList<>(columns * rows);
        BufferedImage tile = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tile.createGraphics();
        try {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    graphics.setComposite(AlphaComposite.Clear);
                    graphics.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(image, -column * MAP_SIZE, -row * MAP_SIZE, null);
                    tiles.add(MapPalette.imageToBytes(tile));
                }
            }
        } finally {
            graphics.dispose();
        }
        return new MapFrameData(image.getWidth(), image.getHeight(), columns, rows, tiles);
    }
}
