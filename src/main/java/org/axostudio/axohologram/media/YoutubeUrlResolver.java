package org.axostudio.axohologram.media;

import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class YoutubeUrlResolver {

    private static final String VIDEO_ID_PATTERN = "^[A-Za-z0-9_-]{11}$";

    private YoutubeUrlResolver() {
    }

    public record ResolvedUrl(URI uri, MediaType downloadType, boolean youtubePreview) {
    }

    public static ResolvedUrl resolve(MediaType requestedType, URI uri, ConfigurationSection mediaRoot) throws IOException {
        if (!isEnabled(mediaRoot) || !isYoutubeHost(uri.getHost())) {
            return new ResolvedUrl(uri, requestedType, false);
        }

        String videoId = extractVideoId(uri);
        if (videoId == null) {
            throw new IOException("Could not extract a YouTube video id from the URL.");
        }

        String quality = normalizeThumbnailQuality(mediaRoot == null ? null : mediaRoot.getString("youtube.thumbnail-quality", "hqdefault"));
        URI thumbnailUri = URI.create("https://img.youtube.com/vi/" + videoId + "/" + quality + ".jpg");
        return new ResolvedUrl(thumbnailUri, MediaType.IMAGE, true);
    }

    public static boolean isEnabled(ConfigurationSection mediaRoot) {
        return mediaRoot == null || mediaRoot.getBoolean("youtube.enabled", true);
    }

    public static boolean isTrustedYoutubeHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("youtube.com")
                || normalized.endsWith(".youtube.com")
                || normalized.equals("youtu.be")
                || normalized.equals("youtube-nocookie.com")
                || normalized.endsWith(".youtube-nocookie.com")
                || normalized.equals("img.youtube.com")
                || normalized.equals("i.ytimg.com");
    }

    private static boolean isYoutubeHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("youtube.com")
                || normalized.endsWith(".youtube.com")
                || normalized.equals("youtu.be")
                || normalized.equals("youtube-nocookie.com")
                || normalized.endsWith(".youtube-nocookie.com");
    }

    private static String extractVideoId(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (host.equals("youtu.be")) {
            return validateVideoId(firstPathSegment(path));
        }

        String queryVideoId = validateVideoId(queryParam(uri.getRawQuery(), "v"));
        if (queryVideoId != null) {
            return queryVideoId;
        }

        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            if (segment.equalsIgnoreCase("shorts")
                    || segment.equalsIgnoreCase("live")
                    || segment.equalsIgnoreCase("embed")
                    || segment.equalsIgnoreCase("v")) {
                String id = validateVideoId(segments[i + 1]);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    private static String firstPathSegment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(0, slash) : normalized;
    }

    private static String queryParam(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String name = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
            if (!key.equals(name)) {
                continue;
            }
            return URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String validateVideoId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim();
        return normalized.matches(VIDEO_ID_PATTERN) ? normalized : null;
    }

    private static String normalizeThumbnailQuality(String raw) {
        if (raw == null || raw.isBlank()) {
            return "hqdefault";
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "maxresdefault", "sddefault", "hqdefault", "mqdefault", "default" -> raw.trim().toLowerCase(Locale.ROOT);
            default -> "hqdefault";
        };
    }
}
