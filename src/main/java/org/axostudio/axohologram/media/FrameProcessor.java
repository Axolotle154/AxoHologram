package org.axostudio.axohologram.media;

import org.jcodec.api.JCodecException;
import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public final class FrameProcessor {

    private static final int MAP_SIZE = 128;
    private static final int MAP_FRAME_CACHE_VERSION = 1;

    private final MediaCacheManager cacheManager;
    private final ThumbnailManager thumbnailManager;

    public FrameProcessor(MediaCacheManager cacheManager, ThumbnailManager thumbnailManager) {
        this.cacheManager = cacheManager;
        this.thumbnailManager = thumbnailManager;
    }

    public ProcessedMedia process(MediaType type, MediaDownloadResult download, MediaSettings settings, int maxFrames) throws IOException {
        if (type == MediaType.VIDEO && download.mediaType() == MediaType.IMAGE) {
            return processImagePreviewAsVideo(download, settings);
        }
        if (type == MediaType.VIDEO) {
            return processVideo(download, settings, maxFrames);
        }
        return processImage(download, settings);
    }

    private ProcessedMedia processImage(MediaDownloadResult download, MediaSettings settings) throws IOException {
        BufferedImage source = ImageIO.read(download.file());
        if (source == null) {
            throw new IOException("Image format is not supported by this server runtime.");
        }

        BufferedImage frame = normalizeForMap(source, settings);
        File thumbnail = thumbnailManager.createThumbnail(frame, cacheManager.thumbnailFile(download.cacheKey()));
        return new ProcessedMedia(MediaType.IMAGE, List.of(frame), List.of(MapFrameData.fromImage(frame)), null, thumbnail, source.getWidth(), source.getHeight());
    }

    private ProcessedMedia processImagePreviewAsVideo(MediaDownloadResult download, MediaSettings settings) throws IOException {
        BufferedImage source = ImageIO.read(download.file());
        if (source == null) {
            throw new IOException("Image preview format is not supported by this server runtime.");
        }

        BufferedImage frame = normalizeForMap(source, settings);
        File thumbnail = thumbnailManager.createThumbnail(frame, cacheManager.thumbnailFile(download.cacheKey()));
        return new ProcessedMedia(MediaType.VIDEO, List.of(frame), List.of(MapFrameData.fromImage(frame)), null, thumbnail, source.getWidth(), source.getHeight());
    }

    private ProcessedMedia processVideo(MediaDownloadResult download, MediaSettings settings, int maxFrames) throws IOException {
        String frameCacheKey = download.cacheKey()
                + "_" + settings.fps()
                + "_" + settings.maxResolution()
                + "_" + settings.width()
                + "_" + settings.height()
                + "_" + settings.scale()
                + "_" + settings.quality().name()
                + "_" + maxFrames;
        File frameDirectory = cacheManager.frameDirectory(frameCacheKey);
        File mapFrameDirectory = cacheManager.mapFrameDirectory(frameCacheKey);
        List<File> cachedMapFrameFiles = listCacheFiles(mapFrameDirectory, ".bin", maxFrames);
        if (!cachedMapFrameFiles.isEmpty()) {
            File thumbnail = cacheManager.thumbnailFile(frameCacheKey);
            createThumbnailIfMissing(frameDirectory, thumbnail);
            MapFrameData firstFrame = readMapFrame(cachedMapFrameFiles.getFirst());
            if (firstFrame != null) {
                return new ProcessedMedia(MediaType.VIDEO, List.of(), List.of(), cachedMapFrameFiles, frameDirectory, thumbnail,
                    firstFrame.width(), firstFrame.height());
            }
        }

        List<File> cachedFrameFiles = listCacheFiles(frameDirectory, ".png", maxFrames);
        if (!cachedFrameFiles.isEmpty()) {
            try {
                ProcessedMedia cached = processCachedPngFrames(cachedFrameFiles, frameDirectory, mapFrameDirectory, frameCacheKey);
                if (cached != null) {
                    return cached;
                }
            } catch (IOException ignored) {
                cleanupAfterFailedVideoProcessing(frameDirectory, mapFrameDirectory);
            }
        }

        try {
            deleteDirectoryContents(frameDirectory);
            deleteDirectoryContents(mapFrameDirectory);
            return switch (download.extension()) {
                case "gif" -> processGifVideo(download.file(), settings, maxFrames, frameDirectory, mapFrameDirectory, frameCacheKey);
                case "mp4" -> processMp4Video(download.file(), settings, maxFrames, frameDirectory, mapFrameDirectory, frameCacheKey);
                case "webm" -> throw new IOException("WEBM validation is supported, but WEBM frame extraction is not available in the bundled Java extractor.");
                default -> throw new IOException("Unsupported video extension: " + download.extension());
            };
        } catch (IOException | RuntimeException exception) {
            cleanupAfterFailedVideoProcessing(frameDirectory, mapFrameDirectory);
            throw exception;
        }
    }

    private List<MapFrameData> createMapFrames(List<BufferedImage> frames) {
        List<MapFrameData> mapFrames = new ArrayList<>(frames.size());
        for (BufferedImage frame : frames) {
            mapFrames.add(MapFrameData.fromImage(frame));
        }
        return List.copyOf(mapFrames);
    }

    private ProcessedMedia processCachedPngFrames(List<File> frameFiles, File frameDirectory, File mapFrameDirectory, String frameCacheKey) throws IOException {
        deleteDirectoryContents(mapFrameDirectory);
        List<File> mapFrameFiles = new ArrayList<>(frameFiles.size());
        BufferedImage firstFrame = null;
        for (File file : frameFiles) {
            BufferedImage frame = ImageIO.read(file);
            if (frame == null) {
                continue;
            }
            if (firstFrame == null) {
                firstFrame = frame;
            }
            mapFrameFiles.add(writeMapFrame(MapFrameData.fromImage(frame), mapFrameFile(mapFrameDirectory, mapFrameFiles.size())));
        }
        if (firstFrame == null || mapFrameFiles.isEmpty()) {
            return null;
        }

        File thumbnail = thumbnailManager.createThumbnail(firstFrame, cacheManager.thumbnailFile(frameCacheKey));
        MapFrameData firstMapFrame = readMapFrame(mapFrameFiles.getFirst());
        return new ProcessedMedia(MediaType.VIDEO, List.of(firstFrame), List.of(), mapFrameFiles, frameDirectory, thumbnail,
                firstMapFrame.width(), firstMapFrame.height());
    }

    private ProcessedMedia processGifVideo(File file, MediaSettings settings, int maxFrames, File frameDirectory, File mapFrameDirectory, String frameCacheKey) throws IOException {
        List<File> mapFrameFiles = new ArrayList<>();
        List<BufferedImage> firstFrame = new ArrayList<>(1);
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                throw new IOException("Could not open GIF stream.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No GIF reader is available.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                for (int index = 0; index < maxFrames; index++) {
                    try {
                        BufferedImage frame = reader.read(index);
                        if (frame == null) {
                            break;
                        }
                        appendVideoFrame(frame, settings, frameDirectory, mapFrameDirectory, mapFrameFiles, firstFrame);
                    } catch (IndexOutOfBoundsException exception) {
                        break;
                    }
                }
            } finally {
                reader.dispose();
            }
        }
        return finishVideoMedia(mapFrameFiles, firstFrame, frameDirectory, frameCacheKey);
    }

    private ProcessedMedia processMp4Video(File file, MediaSettings settings, int maxFrames, File frameDirectory, File mapFrameDirectory, String frameCacheKey) throws IOException {
        List<File> mapFrameFiles = new ArrayList<>();
        List<BufferedImage> firstFrame = new ArrayList<>(1);
        try (SeekableByteChannel channel = NIOUtils.readableChannel(file)) {
            AWTFrameGrab grab = AWTFrameGrab.createAWTFrameGrab(channel);
            double duration = grab.getVideoTrack() == null || grab.getVideoTrack().getMeta() == null
                    ? 0.0D
                    : grab.getVideoTrack().getMeta().getTotalDuration();
            double step = 1.0D / Math.max(1, settings.fps());

            if (duration > 0.0D) {
                int targetFrames = Math.min(maxFrames, Math.max(1, (int) Math.ceil(duration * settings.fps())));
                for (int index = 0; index < targetFrames; index++) {
                    grab.seekToSecondSloppy(index * step);
                    BufferedImage frame = grab.getFrame();
                    if (frame == null) {
                        break;
                    }
                    appendVideoFrame(frame, settings, frameDirectory, mapFrameDirectory, mapFrameFiles, firstFrame);
                }
            } else {
                for (int index = 0; index < maxFrames; index++) {
                    BufferedImage frame = grab.getFrame();
                    if (frame == null) {
                        break;
                    }
                    appendVideoFrame(frame, settings, frameDirectory, mapFrameDirectory, mapFrameFiles, firstFrame);
                }
            }
            return finishVideoMedia(mapFrameFiles, firstFrame, frameDirectory, frameCacheKey);
        } catch (JCodecException exception) {
            throw new IOException("Could not decode MP4 video. Only codecs supported by JCodec can be extracted.", exception);
        }
    }

    private void appendVideoFrame(
            BufferedImage source,
            MediaSettings settings,
            File frameDirectory,
            File mapFrameDirectory,
            List<File> mapFrameFiles,
            List<BufferedImage> firstFrame
    ) throws IOException {
        BufferedImage frame = normalizeForMap(source, settings);
        int frameIndex = mapFrameFiles.size();
        saveFrame(frame, frameDirectory, frameIndex);
        MapFrameData mapFrame = MapFrameData.fromImage(frame);
        mapFrameFiles.add(writeMapFrame(mapFrame, mapFrameFile(mapFrameDirectory, frameIndex)));
        if (firstFrame.isEmpty()) {
            firstFrame.add(frame);
        }
    }

    private ProcessedMedia finishVideoMedia(List<File> mapFrameFiles, List<BufferedImage> firstFrame, File frameDirectory, String frameCacheKey) throws IOException {
        if (mapFrameFiles.isEmpty() || firstFrame.isEmpty()) {
            throw new IOException("No frames could be extracted from the media file.");
        }

        File thumbnail = thumbnailManager.createThumbnail(firstFrame.getFirst(), cacheManager.thumbnailFile(frameCacheKey));
        MapFrameData firstMapFrame = readMapFrame(mapFrameFiles.getFirst());
        if (firstMapFrame == null) {
            throw new IOException("Could not read processed video frame cache.");
        }
        return new ProcessedMedia(MediaType.VIDEO, List.of(firstFrame.getFirst()), List.of(), mapFrameFiles, frameDirectory, thumbnail,
                firstMapFrame.width(), firstMapFrame.height());
    }

    private List<MapFrameData> loadCachedMapFrames(File frameDirectory, int maxFrames) throws IOException {
        if (frameDirectory == null || !frameDirectory.isDirectory()) {
            return List.of();
        }

        File[] files = frameDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".bin"));
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        sortedFiles.sort(Comparator.comparing(File::getName));
        List<MapFrameData> frames = new ArrayList<>(Math.min(sortedFiles.size(), maxFrames));
        for (File file : sortedFiles) {
            if (frames.size() >= maxFrames) {
                break;
            }
            MapFrameData frame;
            try {
                frame = readMapFrame(file);
            } catch (IOException exception) {
                return List.of();
            }
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
    }

    private List<File> listCacheFiles(File directory, String extension, int maxFiles) {
        if (directory == null || !directory.isDirectory()) {
            return List.of();
        }

        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(extension));
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        sortedFiles.sort(Comparator.comparing(File::getName));
        int limit = Math.max(1, Math.min(maxFiles, sortedFiles.size()));
        return List.copyOf(sortedFiles.subList(0, limit));
    }

    private MapFrameData readMapFrame(File file) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int version = input.readInt();
            if (version != MAP_FRAME_CACHE_VERSION) {
                return null;
            }
            int width = input.readInt();
            int height = input.readInt();
            int columns = input.readInt();
            int rows = input.readInt();
            int tileCount = input.readInt();
            if (width <= 0 || height <= 0 || columns <= 0 || rows <= 0 || tileCount != columns * rows) {
                return null;
            }

            List<byte[]> tiles = new ArrayList<>(tileCount);
            for (int index = 0; index < tileCount; index++) {
                byte[] pixels = new byte[MAP_SIZE * MAP_SIZE];
                input.readFully(pixels);
                tiles.add(pixels);
            }
            return new MapFrameData(width, height, columns, rows, tiles);
        }
    }

    private void saveMapFrames(List<MapFrameData> frames, File frameDirectory) throws IOException {
        if (!frameDirectory.exists() && !frameDirectory.mkdirs()) {
            throw new IOException("Could not create map frame cache folder.");
        }
        for (int index = 0; index < frames.size(); index++) {
            File frameFile = new File(frameDirectory, String.format("frame_%05d.bin", index));
            writeMapFrame(frames.get(index), frameFile);
        }
    }

    private File writeMapFrame(MapFrameData frame, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create map frame cache folder.");
        }
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeInt(MAP_FRAME_CACHE_VERSION);
            output.writeInt(frame.width());
            output.writeInt(frame.height());
            output.writeInt(frame.columns());
            output.writeInt(frame.rows());
            output.writeInt(frame.tileCount());
            for (byte[] tile : frame.tiles()) {
                output.write(tile, 0, MAP_SIZE * MAP_SIZE);
            }
        }
        return file;
    }

    private void createThumbnailIfMissing(File frameDirectory, File thumbnail) throws IOException {
        if (thumbnail == null || thumbnail.isFile()) {
            return;
        }
        List<File> firstFrame = listCacheFiles(frameDirectory, ".png", 1);
        if (!firstFrame.isEmpty()) {
            BufferedImage frame = ImageIO.read(firstFrame.getFirst());
            if (frame != null) {
                thumbnailManager.createThumbnail(frame, thumbnail);
            }
        }
    }

    private List<BufferedImage> readGifFrames(File file, MediaSettings settings, int maxFrames) throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                throw new IOException("Could not open GIF stream.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No GIF reader is available.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                for (int index = 0; index < maxFrames; index++) {
                    try {
                        BufferedImage frame = reader.read(index);
                        if (frame == null) {
                            break;
                        }
                        frames.add(normalizeForMap(frame, settings));
                    } catch (IndexOutOfBoundsException exception) {
                        break;
                    }
                }
            } finally {
                reader.dispose();
            }
        }
        return frames;
    }

    private List<BufferedImage> readMp4Frames(File file, MediaSettings settings, int maxFrames) throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        try (SeekableByteChannel channel = NIOUtils.readableChannel(file)) {
            AWTFrameGrab grab = AWTFrameGrab.createAWTFrameGrab(channel);
            double duration = grab.getVideoTrack() == null || grab.getVideoTrack().getMeta() == null
                    ? 0.0D
                    : grab.getVideoTrack().getMeta().getTotalDuration();
            double step = 1.0D / Math.max(1, settings.fps());

            if (duration > 0.0D) {
                int targetFrames = Math.min(maxFrames, Math.max(1, (int) Math.ceil(duration * settings.fps())));
                for (int index = 0; index < targetFrames; index++) {
                    grab.seekToSecondSloppy(index * step);
                    BufferedImage frame = grab.getFrame();
                    if (frame == null) {
                        break;
                    }
                    frames.add(normalizeForMap(frame, settings));
                }
                return frames;
            }

            for (int index = 0; index < maxFrames; index++) {
                BufferedImage frame = grab.getFrame();
                if (frame == null) {
                    break;
                }
                frames.add(normalizeForMap(frame, settings));
            }
            return frames;
        } catch (JCodecException exception) {
            throw new IOException("Could not decode MP4 video. Only codecs supported by JCodec can be extracted.", exception);
        }
    }

    private List<BufferedImage> loadCachedFrames(File frameDirectory, int maxFrames) throws IOException {
        if (frameDirectory == null || !frameDirectory.isDirectory()) {
            return List.of();
        }

        File[] files = frameDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        sortedFiles.sort(Comparator.comparing(File::getName));
        List<BufferedImage> frames = new ArrayList<>(Math.min(sortedFiles.size(), maxFrames));
        for (File file : sortedFiles) {
            if (frames.size() >= maxFrames) {
                break;
            }
            BufferedImage frame = ImageIO.read(file);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
    }

    private void saveFrames(List<BufferedImage> frames, File frameDirectory) throws IOException {
        if (!frameDirectory.exists() && !frameDirectory.mkdirs()) {
            throw new IOException("Could not create frame cache folder.");
        }
        for (int index = 0; index < frames.size(); index++) {
            saveFrame(frames.get(index), frameDirectory, index);
        }
    }

    private File saveFrame(BufferedImage frame, File frameDirectory, int index) throws IOException {
        if (!frameDirectory.exists() && !frameDirectory.mkdirs()) {
            throw new IOException("Could not create frame cache folder.");
        }
        File frameFile = new File(frameDirectory, String.format("frame_%05d.png", index));
        ImageIO.write(frame, "png", frameFile);
        return frameFile;
    }

    private File mapFrameFile(File mapFrameDirectory, int index) {
        return new File(mapFrameDirectory, String.format("frame_%05d.bin", index));
    }

    private void cleanupAfterFailedVideoProcessing(File frameDirectory, File mapFrameDirectory) {
        try {
            deleteDirectoryContents(frameDirectory);
            deleteDirectoryContents(mapFrameDirectory);
        } catch (IOException ignored) {
        }
    }

    private void deleteDirectoryContents(File directory) throws IOException {
        if (directory == null || !directory.exists()) {
            return;
        }
        Path root = directory.toPath();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> targets = paths
                    .filter(path -> !path.equals(root))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path target : targets) {
                Files.deleteIfExists(target);
            }
        }
    }

    private BufferedImage normalizeForMap(BufferedImage source, MediaSettings settings) {
        MediaSettings resolvedSettings = settings == null ? new MediaSettings(1.0D, 1.0D, 1.0D, 32, MAP_SIZE,
                MediaQuality.MEDIUM, false, 0.0F, 1, false, true) : settings;
        int columns = tileCount(resolvedSettings.width(), resolvedSettings.scale(), resolvedSettings.maxResolution());
        int rows = tileCount(resolvedSettings.height(), resolvedSettings.scale(), resolvedSettings.maxResolution());
        int canvasWidth = columns * MAP_SIZE;
        int canvasHeight = rows * MAP_SIZE;
        BufferedImage output = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(new Color(0, 0, 0, 0));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation(resolvedSettings.quality()));
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            double factor = Math.min((double) canvasWidth / source.getWidth(), (double) canvasHeight / source.getHeight());
            int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * factor));
            int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * factor));
            int x = (canvasWidth - targetWidth) / 2;
            int y = (canvasHeight - targetHeight) / 2;
            graphics.drawImage(source, x, y, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private int tileCount(double size, double scale, int maxResolution) {
        int requested = (int) Math.ceil(Math.max(0.1D, size) * Math.max(0.01D, scale));
        int maxTiles = Math.max(1, Math.max(MAP_SIZE, maxResolution) / MAP_SIZE);
        return Math.max(1, Math.min(requested, maxTiles));
    }

    private Object interpolation(MediaQuality quality) {
        return switch (quality == null ? MediaQuality.MEDIUM : quality) {
            case LOW -> RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
            case MEDIUM -> RenderingHints.VALUE_INTERPOLATION_BILINEAR;
            case HIGH -> RenderingHints.VALUE_INTERPOLATION_BICUBIC;
        };
    }
}
