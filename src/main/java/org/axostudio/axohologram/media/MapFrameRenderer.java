package org.axostudio.axohologram.media;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public final class MapFrameRenderer extends MapRenderer {

    private static final int MAP_SIZE = 128;
    private static final int MAP_PIXELS = MAP_SIZE * MAP_SIZE;
    private static final int DELTA_THRESHOLD = (int) (MAP_PIXELS * 0.75D);
    private static final int[] FULL_RENDER = fullRenderIndexes();

    private final AtomicLong version = new AtomicLong();
    private volatile byte[] pixels = new byte[MAP_PIXELS];
    private volatile int[] changedIndexes = FULL_RENDER;
    private volatile long changedFromVersion;
    private volatile long renderedVersion = -1L;

    public MapFrameRenderer(BufferedImage image) {
        this(MapFrameData.fromImage(image).tile(0, 0));
    }

    public MapFrameRenderer(byte[] pixels) {
        super(false);
        setPixels(pixels);
    }

    public boolean setPixels(byte[] pixels) {
        if (pixels == null || pixels.length < MAP_PIXELS) {
            return false;
        }
        byte[] currentPixels = this.pixels;
        if (currentPixels == pixels || Arrays.equals(currentPixels, pixels)) {
            return false;
        }
        long previousVersion = version.get();
        this.changedIndexes = changedIndexes(currentPixels, pixels);
        this.changedFromVersion = previousVersion;
        this.pixels = pixels;
        version.incrementAndGet();
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public synchronized void render(MapView map, MapCanvas canvas, Player player) {
        long currentVersion = version.get();
        if (renderedVersion == currentVersion) {
            return;
        }

        byte[] currentPixels = pixels;
        int[] indexes = renderedVersion == changedFromVersion ? changedIndexes : FULL_RENDER;
        renderPixels(canvas, currentPixels, indexes);
        renderedVersion = currentVersion;
    }

    @SuppressWarnings("deprecation")
    private void renderPixels(MapCanvas canvas, byte[] pixels, int[] indexes) {
        for (int index : indexes) {
            canvas.setPixel(index & 127, index >> 7, pixels[index]);
        }
    }

    private int[] changedIndexes(byte[] currentPixels, byte[] nextPixels) {
        int count = 0;
        for (int index = 0; index < MAP_PIXELS; index++) {
            if (currentPixels[index] != nextPixels[index]) {
                count++;
                if (count > DELTA_THRESHOLD) {
                    return FULL_RENDER;
                }
            }
        }
        int[] changed = new int[count];
        int writeIndex = 0;
        for (int index = 0; index < MAP_PIXELS; index++) {
            if (currentPixels[index] != nextPixels[index]) {
                changed[writeIndex++] = index;
            }
        }
        return changed;
    }

    private static int[] fullRenderIndexes() {
        int[] indexes = new int[MAP_PIXELS];
        for (int index = 0; index < MAP_PIXELS; index++) {
            indexes[index] = index;
        }
        return indexes;
    }
}
