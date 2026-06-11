package org.axostudio.axohologram.media;

import org.axostudio.axohologram.AxoHologram;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class MediaCacheManager {

    private final AxoHologram plugin;
    private final File cacheFolder;
    private final File imagesFolder;
    private final File videosFolder;
    private final File framesFolder;
    private final File mapFramesFolder;
    private final File thumbnailsFolder;
    private final File backupsFolder;

    public MediaCacheManager(AxoHologram plugin) {
        this.plugin = plugin;
        this.cacheFolder = new File(plugin.getDataFolder(), "cache");
        this.imagesFolder = new File(cacheFolder, "images");
        this.videosFolder = new File(cacheFolder, "videos");
        this.framesFolder = new File(cacheFolder, "frames");
        this.mapFramesFolder = new File(cacheFolder, "map-frames");
        this.thumbnailsFolder = new File(cacheFolder, "thumbnails");
        this.backupsFolder = new File(plugin.getDataFolder(), "backups");
    }

    public void ensureFolders() {
        mkdir(cacheFolder);
        mkdir(imagesFolder);
        mkdir(videosFolder);
        mkdir(framesFolder);
        mkdir(mapFramesFolder);
        mkdir(thumbnailsFolder);
        mkdir(backupsFolder);
    }

    public File cachedMediaFile(MediaType type, URI uri, String extension) {
        String normalizedExtension = normalizeExtension(extension);
        File root = type == MediaType.VIDEO ? videosFolder : imagesFolder;
        return new File(root, cacheKey(uri) + "." + normalizedExtension);
    }

    public File frameDirectory(String cacheKey) {
        return new File(framesFolder, sanitizeCacheKey(cacheKey));
    }

    public File mapFrameDirectory(String cacheKey) {
        return new File(mapFramesFolder, sanitizeCacheKey(cacheKey));
    }

    public File thumbnailFile(String cacheKey) {
        return new File(thumbnailsFolder, sanitizeCacheKey(cacheKey) + ".png");
    }

    public File backupsFolder() {
        return backupsFolder;
    }

    public String cacheKey(URI uri) {
        String source = uri == null ? "" : uri.normalize().toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(source.hashCode());
        }
    }

    private void mkdir(File folder) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create folder: " + folder.getAbsolutePath());
        }
    }

    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "bin";
        }
        String normalized = extension.toLowerCase(Locale.ROOT).replace(".", "");
        return normalized.matches("[a-z0-9]{1,8}") ? normalized : "bin";
    }

    private String sanitizeCacheKey(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return "unknown";
        }
        return cacheKey.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
