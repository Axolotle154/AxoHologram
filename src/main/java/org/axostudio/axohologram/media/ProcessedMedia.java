package org.axostudio.axohologram.media;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProcessedMedia {

    private static final int MAP_SIZE = 128;
    private static final int MAP_FRAME_CACHE_VERSION = 1;
    private static final int MAP_FRAME_MEMORY_CACHE_SIZE = 8;

    private final MediaType type;
    private final List<BufferedImage> frames;
    private final List<MapFrameData> mapFrames;
    private final List<File> mapFrameFiles;
    private final File frameDirectory;
    private final File thumbnailFile;
    private final int width;
    private final int height;
    private final Map<Integer, MapFrameData> loadedMapFrames;

    public ProcessedMedia(
            MediaType type,
            List<BufferedImage> frames,
            List<MapFrameData> mapFrames,
            File frameDirectory,
            File thumbnailFile,
            int width,
            int height
    ) {
        this(type, frames, mapFrames, List.of(), frameDirectory, thumbnailFile, width, height);
    }

    public ProcessedMedia(
            MediaType type,
            List<BufferedImage> frames,
            List<MapFrameData> mapFrames,
            List<File> mapFrameFiles,
            File frameDirectory,
            File thumbnailFile,
            int width,
            int height
    ) {
        this.type = type;
        this.frames = frames == null ? List.of() : List.copyOf(frames);
        this.mapFrames = mapFrames == null ? List.of() : List.copyOf(mapFrames);
        this.mapFrameFiles = mapFrameFiles == null ? List.of() : List.copyOf(mapFrameFiles);
        this.frameDirectory = frameDirectory;
        this.thumbnailFile = thumbnailFile;
        this.width = width;
        this.height = height;
        this.loadedMapFrames = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, MapFrameData> eldest) {
                return size() > MAP_FRAME_MEMORY_CACHE_SIZE;
            }
        });
    }

    public MediaType type() {
        return type;
    }

    public List<BufferedImage> frames() {
        return frames;
    }

    public List<MapFrameData> mapFrames() {
        return mapFrames;
    }

    public List<File> mapFrameFiles() {
        return mapFrameFiles;
    }

    public File frameDirectory() {
        return frameDirectory;
    }

    public File thumbnailFile() {
        return thumbnailFile;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public BufferedImage firstFrame() {
        return frames.isEmpty() ? null : frames.getFirst();
    }

    public MapFrameData firstMapFrame() {
        return mapFrame(0);
    }

    public MapFrameData mapFrame(int index) {
        if (!mapFrames.isEmpty()) {
            return mapFrames.get(clampFrameIndex(index, mapFrames.size()));
        }
        if (mapFrameFiles.isEmpty()) {
            return null;
        }

        int resolvedIndex = clampFrameIndex(index, mapFrameFiles.size());
        synchronized (loadedMapFrames) {
            MapFrameData cached = loadedMapFrames.get(resolvedIndex);
            if (cached != null) {
                return cached;
            }
        }

        try {
            MapFrameData loaded = readMapFrame(mapFrameFiles.get(resolvedIndex));
            if (loaded == null) {
                return null;
            }
            synchronized (loadedMapFrames) {
                loadedMapFrames.put(resolvedIndex, loaded);
            }
            return loaded;
        } catch (IOException ignored) {
            return null;
        }
    }

    public int frameCount() {
        if (!mapFrameFiles.isEmpty()) {
            return mapFrameFiles.size();
        }
        return mapFrames.isEmpty() ? frames.size() : mapFrames.size();
    }

    private int clampFrameIndex(int index, int frameCount) {
        return Math.max(0, Math.min(index, frameCount - 1));
    }

    private MapFrameData readMapFrame(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int version = input.readInt();
            if (version != MAP_FRAME_CACHE_VERSION) {
                return null;
            }
            int frameWidth = input.readInt();
            int frameHeight = input.readInt();
            int columns = input.readInt();
            int rows = input.readInt();
            int tileCount = input.readInt();
            if (frameWidth <= 0 || frameHeight <= 0 || columns <= 0 || rows <= 0 || tileCount != columns * rows) {
                return null;
            }

            byte[][] rawTiles = new byte[tileCount][];
            for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
                byte[] pixels = new byte[MAP_SIZE * MAP_SIZE];
                input.readFully(pixels);
                rawTiles[tileIndex] = pixels;
            }
            return new MapFrameData(frameWidth, frameHeight, columns, rows, List.of(rawTiles));
        }
    }
}
