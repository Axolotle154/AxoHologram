package org.axostudio.axohologram.media;

import org.axostudio.axohologram.AxoHologram;
import org.bukkit.configuration.ConfigurationSection;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaDownloader {

    private static final int MAX_DOWNLOAD_STEPS = 8;
    private static final long MAX_HTML_PROBE_BYTES = 2L * 1024L * 1024L;

    private final AxoHologram plugin;
    private final MediaCacheManager cacheManager;

    public MediaDownloader(AxoHologram plugin, MediaCacheManager cacheManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
    }

    public MediaDownloadResult download(MediaType type, URI originalUri) throws IOException {
        if (type == null || originalUri == null) {
            throw new IOException("Media URL is missing.");
        }

        ConfigurationSection root = rootConfig();
        YoutubeUrlResolver.ResolvedUrl resolvedUrl = YoutubeUrlResolver.resolve(type, originalUri, root);
        boolean allowPrivateAddresses = root.getBoolean("urls.allow-private-addresses", false);
        URI uri = validateUri(normalizeGoogleDriveUri(resolvedUrl.uri()), allowPrivateAddresses);
        MediaType downloadType = resolvedUrl.downloadType();
        boolean cacheEnabled = root.getBoolean(downloadType == MediaType.VIDEO ? "videos.cache" : "images.cache", true);
        long maxBytes = resolveMaxBytes(downloadType, root);
        int timeoutMillis = Math.max(1, root.getInt("urls.timeout-seconds", 10)) * 1000;
        Map<String, String> cookies = new LinkedHashMap<>();

        URI current = uri;
        for (int step = 0; step < MAX_DOWNLOAD_STEPS; step++) {
            HttpURLConnection connection = openConnection(current, timeoutMillis, cookies);
            int response = connection.getResponseCode();
            storeCookies(connection, cookies);
            if (isRedirect(response)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) {
                    throw new IOException("Redirect response did not include Location.");
                }
                current = validateUri(current.resolve(location), allowPrivateAddresses);
                continue;
            }
            if (response < 200 || response >= 300) {
                connection.disconnect();
                throw new IOException("Remote server returned HTTP " + response + ".");
            }

            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxBytes) {
                connection.disconnect();
                throw new IOException("Media file is " + formatBytes(contentLength)
                        + ", configured limit is " + formatBytes(maxBytes) + ".");
            }

            String contentType = normalizeMime(connection.getContentType());
            String extension = extensionFromPathOrMime(current, contentType, downloadType);
            File target = cacheManager.cachedMediaFile(downloadType, uri, extension);
            String cacheKey = cacheManager.cacheKey(uri);
            if (cacheEnabled && target.isFile() && target.length() > 0L) {
                validateCachedSignature(downloadType, target);
                return new MediaDownloadResult(current, target, downloadType, contentType, extension, target.length(), cacheKey);
            }

            File temp = new File(target.getParentFile(), target.getName() + ".tmp");
            long bytes = downloadBody(connection, temp, maxBytes);
            connection.disconnect();

            byte[] signature = readSignature(temp);
            String signatureExtension = extensionFromSignature(signature);
            if (!isAllowedSignature(downloadType, signatureExtension)) {
                URI confirmationUri = googleDriveConfirmationUri(current, contentType, temp, cookies);
                boolean googleDriveHtml = confirmationUri == null && isGoogleDriveHtml(current, contentType, temp);
                Files.deleteIfExists(temp.toPath());
                if (confirmationUri != null) {
                    current = validateUri(confirmationUri, allowPrivateAddresses);
                    continue;
                }
                if (googleDriveHtml) {
                    throw new IOException("Google Drive did not return the media file. Make sure the file is public and shared with anyone who has the link.");
                }
                throw new IOException("Downloaded file type is not supported.");
            }

            if (contentType != null && !isAllowedMime(downloadType, contentType) && !isGenericDownloadMime(contentType)) {
                Files.deleteIfExists(temp.toPath());
                throw new IOException("Remote MIME type is not supported: " + contentType);
            }

            extension = signatureExtension == null ? extension : signatureExtension;
            target = cacheManager.cachedMediaFile(downloadType, uri, extension);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return new MediaDownloadResult(current, target, downloadType, contentType, extension, bytes, cacheKey);
        }

        throw new IOException("Too many redirects or confirmation steps while downloading media.");
    }

    private URI validateUri(URI uri, boolean allowPrivateAddresses) throws IOException {
        URI normalized = uri.normalize();
        String scheme = normalized.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IOException("Only HTTP and HTTPS URLs are supported.");
        }

        if (normalized.getUserInfo() != null) {
            throw new IOException("Media URLs with embedded credentials are not allowed.");
        }

        String host = normalized.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("Media URL host is missing.");
        }
        if (!allowPrivateAddresses) {
            validatePublicHost(host);
        }
        return normalized;
    }

    private void validatePublicHost(String host) throws IOException {
        String normalizedHost = normalizeHost(host);
        if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")) {
            throw new IOException("Media URL host resolves to a local address.");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalizedHost);
        } catch (UnknownHostException exception) {
            throw new IOException("Media URL host could not be resolved.", exception);
        }
        if (addresses.length == 0) {
            throw new IOException("Media URL host could not be resolved.");
        }
        for (InetAddress address : addresses) {
            if (isPrivateOrLocalAddress(address)) {
                throw new IOException("Media URL host resolves to a private or local address.");
            }
        }
    }

    private String normalizeHost(String host) throws IOException {
        String trimmed = host.trim();
        if (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            throw new IOException("Media URL host is invalid.");
        }
        try {
            return trimmed.indexOf(':') >= 0
                    ? trimmed.toLowerCase(Locale.ROOT)
                    : IDN.toASCII(trimmed).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Media URL host is invalid.", exception);
        }
    }

    private boolean isPrivateOrLocalAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 10
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || first == 0
                    || first >= 224;
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xFF;
            return (first & 0xFE) == 0xFC;
        }
        return true;
    }

    private HttpURLConnection openConnection(URI uri, int timeoutMillis, Map<String, String> cookies) throws IOException {
        try {
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "AxoHologram-Media/1.0");
            connection.setRequestProperty("Accept", "*/*");
            if (!cookies.isEmpty()) {
                connection.setRequestProperty("Cookie", cookieHeader(cookies));
            }
            return connection;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid media URL.", exception);
        }
    }

    private long downloadBody(HttpURLConnection connection, File target, long maxBytes) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create media cache folder.");
        }

        long total = 0L;
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Media file is larger than the configured limit of " + formatBytes(maxBytes) + ".");
                }
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            Files.deleteIfExists(target.toPath());
            throw exception;
        }
        return total;
    }

    private void validateCachedSignature(MediaType type, File file) throws IOException {
        String extension = extensionFromSignature(readSignature(file));
        if (!isAllowedSignature(type, extension)) {
            Files.deleteIfExists(file.toPath());
            throw new IOException("Cached media file type is not supported.");
        }
    }

    private ConfigurationSection rootConfig() {
        ConfigurationSection root = plugin.getConfigManager().getMedia().getConfigurationSection("media-system");
        return root == null ? plugin.getConfigManager().getMedia() : root;
    }

    private long resolveMaxBytes(MediaType type, ConfigurationSection root) {
        int mb = root.getInt(type == MediaType.VIDEO ? "videos.max-file-size-mb" : "images.max-file-size-mb", type == MediaType.VIDEO ? 250 : 10);
        return Math.max(1L, mb) * 1024L * 1024L;
    }

    private String formatBytes(long bytes) {
        double mb = bytes / 1024.0D / 1024.0D;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    private boolean isRedirect(int response) {
        return response == HttpURLConnection.HTTP_MOVED_PERM
                || response == HttpURLConnection.HTTP_MOVED_TEMP
                || response == HttpURLConnection.HTTP_SEE_OTHER
                || response == 307
                || response == 308;
    }

    private String normalizeMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int separator = contentType.indexOf(';');
        String raw = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private String extensionFromPathOrMime(URI uri, String mime, MediaType type) throws IOException {
        String path = uri.getPath();
        if (path != null) {
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < path.length()) {
                String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (isAllowedExtension(type, extension)) {
                    return extension;
                }
            }
        }
        String extension = extensionFromMime(mime);
        if (extension != null && isAllowedExtension(type, extension)) {
            return extension;
        }
        return type == MediaType.VIDEO ? "mp4" : "png";
    }

    private String extensionFromMime(String mime) {
        if (mime == null) {
            return null;
        }
        return switch (mime) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "video/mp4", "application/mp4" -> "mp4";
            case "video/webm" -> "webm";
            default -> null;
        };
    }

    private boolean isAllowedMime(MediaType type, String mime) {
        String extension = extensionFromMime(mime);
        return extension != null && isAllowedExtension(type, extension);
    }

    private boolean isGenericDownloadMime(String mime) {
        if (mime == null) {
            return false;
        }
        return mime.equals("application/octet-stream")
                || mime.equals("binary/octet-stream")
                || mime.equals("application/download")
                || mime.equals("application/force-download")
                || mime.equals("application/x-download");
    }

    private boolean isAllowedExtension(MediaType type, String extension) {
        if (extension == null) {
            return false;
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        if (type == MediaType.VIDEO) {
            return normalized.equals("mp4") || normalized.equals("webm") || normalized.equals("gif");
        }
        return normalized.equals("png")
                || normalized.equals("jpg")
                || normalized.equals("jpeg")
                || normalized.equals("webp")
                || normalized.equals("gif");
    }

    private boolean isAllowedSignature(MediaType type, String extension) {
        return extension != null && isAllowedExtension(type, extension);
    }

    private byte[] readSignature(File file) throws IOException {
        byte[] signature = new byte[16];
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            int read = input.read(signature);
            if (read <= 0) {
                return new byte[0];
            }
            if (read < signature.length) {
                byte[] resized = new byte[read];
                System.arraycopy(signature, 0, resized, 0, read);
                return resized;
            }
            return signature;
        }
    }

    private String extensionFromSignature(byte[] signature) {
        if (signature == null || signature.length < 4) {
            return null;
        }
        if ((signature[0] & 0xFF) == 0x89 && signature[1] == 0x50 && signature[2] == 0x4E && signature[3] == 0x47) {
            return "png";
        }
        if ((signature[0] & 0xFF) == 0xFF && (signature[1] & 0xFF) == 0xD8 && (signature[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (signature[0] == 0x47 && signature[1] == 0x49 && signature[2] == 0x46 && signature[3] == 0x38) {
            return "gif";
        }
        if (signature.length >= 12
                && signature[0] == 0x52 && signature[1] == 0x49 && signature[2] == 0x46 && signature[3] == 0x46
                && signature[8] == 0x57 && signature[9] == 0x45 && signature[10] == 0x42 && signature[11] == 0x50) {
            return "webp";
        }
        if (signature.length >= 12
                && signature[4] == 0x66 && signature[5] == 0x74 && signature[6] == 0x79 && signature[7] == 0x70) {
            return "mp4";
        }
        if ((signature[0] & 0xFF) == 0x1A && signature[1] == 0x45 && (signature[2] & 0xFF) == 0xDF && (signature[3] & 0xFF) == 0xA3) {
            return "webm";
        }
        return null;
    }

    private URI normalizeGoogleDriveUri(URI uri) {
        if (uri == null || !isGoogleDriveHost(uri.getHost())) {
            return uri;
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("drive.google.com") && !host.endsWith(".drive.google.com")) {
            return uri;
        }

        String id = queryParam(uri.getRawQuery(), "id");
        if (id == null) {
            id = googleDriveFileIdFromPath(uri.getPath());
        }
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
            return uri;
        }
        return URI.create("https://drive.google.com/uc?export=download&id=" + urlEncode(id));
    }

    private URI googleDriveConfirmationUri(URI current, String contentType, File body, Map<String, String> cookies) {
        if (!isGoogleDriveHost(current.getHost()) || !shouldProbeHtml(contentType, body)) {
            return null;
        }

        String html = readHtmlProbe(body);
        if (html == null || !looksLikeHtml(html)) {
            return googleDriveCookieConfirmationUri(current, cookies);
        }

        URI formUri = googleDriveFormUri(html, current);
        if (formUri != null) {
            return formUri;
        }

        URI hrefUri = googleDriveHrefUri(html, current);
        if (hrefUri != null) {
            return hrefUri;
        }

        return googleDriveCookieConfirmationUri(current, cookies);
    }

    private URI googleDriveFormUri(String html, URI base) {
        Matcher formMatcher = Pattern.compile("(?is)<form\\b([^>]*)>(.*?)</form>").matcher(html);
        while (formMatcher.find()) {
            String action = attribute(formMatcher.group(1), "action");
            if (action == null || action.isBlank()) {
                continue;
            }

            URI actionUri = base.resolve(htmlDecode(action));
            if (!isGoogleDriveHost(actionUri.getHost())) {
                continue;
            }

            Map<String, String> params = new LinkedHashMap<>();
            Matcher inputMatcher = Pattern.compile("(?is)<input\\b([^>]*)>").matcher(formMatcher.group(2));
            while (inputMatcher.find()) {
                String name = attribute(inputMatcher.group(1), "name");
                String value = attribute(inputMatcher.group(1), "value");
                if (name != null && value != null) {
                    params.put(htmlDecode(name), htmlDecode(value));
                }
            }

            if (isGoogleDriveDownloadUri(actionUri) || params.containsKey("confirm")) {
                return appendQueryParameters(actionUri, params);
            }
        }
        return null;
    }

    private URI googleDriveHrefUri(String html, URI base) {
        Matcher hrefMatcher = Pattern.compile("(?is)href\\s*=\\s*([\"'])(.*?)\\1").matcher(html);
        while (hrefMatcher.find()) {
            URI candidate = base.resolve(htmlDecode(hrefMatcher.group(2)));
            if (isGoogleDriveDownloadUri(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private URI googleDriveCookieConfirmationUri(URI current, Map<String, String> cookies) {
        String id = queryParam(current.getRawQuery(), "id");
        if (id == null) {
            id = googleDriveFileIdFromPath(current.getPath());
        }
        if (id == null || id.isBlank()) {
            return null;
        }

        String confirm = null;
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (entry.getKey().startsWith("download_warning")) {
                confirm = entry.getValue();
                break;
            }
        }
        if (confirm == null || confirm.isBlank()) {
            return null;
        }

        return URI.create("https://drive.google.com/uc?export=download&id=" + urlEncode(id)
                + "&confirm=" + urlEncode(confirm));
    }

    private boolean isGoogleDriveDownloadUri(URI uri) {
        if (uri == null || !isGoogleDriveHost(uri.getHost())) {
            return false;
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery().toLowerCase(Locale.ROOT);
        return (path.equals("/uc") || path.endsWith("/download") || path.contains("/download"))
                && query.contains("confirm=");
    }

    private boolean isGoogleDriveHtml(URI current, String contentType, File body) {
        return isGoogleDriveHost(current.getHost()) && shouldProbeHtml(contentType, body) && looksLikeHtml(readHtmlProbe(body));
    }

    private boolean shouldProbeHtml(String contentType, File body) {
        if (body == null || !body.isFile() || body.length() > MAX_HTML_PROBE_BYTES) {
            return false;
        }
        return contentType == null || contentType.equals("text/html") || contentType.equals("application/xhtml+xml");
    }

    private String readHtmlProbe(File body) {
        try {
            return Files.readString(body.toPath(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean looksLikeHtml(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.stripLeading().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("<!doctype html")
                || trimmed.startsWith("<html")
                || trimmed.contains("<html")
                || trimmed.contains("<form");
    }

    private void storeCookies(HttpURLConnection connection, Map<String, String> cookies) {
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() == null || !"set-cookie".equalsIgnoreCase(header.getKey()) || header.getValue() == null) {
                continue;
            }
            for (String value : header.getValue()) {
                int separator = value == null ? -1 : value.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                int end = value.indexOf(';', separator);
                String name = value.substring(0, separator).trim();
                String cookieValue = end >= 0 ? value.substring(separator + 1, end).trim() : value.substring(separator + 1).trim();
                if (!name.isBlank()) {
                    cookies.put(name, cookieValue);
                }
            }
        }
    }

    private String cookieHeader(Map<String, String> cookies) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private URI appendQueryParameters(URI uri, Map<String, String> params) {
        if (params.isEmpty()) {
            return uri;
        }

        StringBuilder builder = new StringBuilder(uri.toString());
        boolean first = uri.getRawQuery() == null;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.append(first ? '?' : '&');
            builder.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
            first = false;
        }
        return URI.create(builder.toString());
    }

    private String googleDriveFileIdFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("/file/d/([^/]+)").matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isGoogleDriveHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("drive.google.com")
                || normalized.endsWith(".drive.google.com")
                || normalized.equals("drive.usercontent.google.com")
                || normalized.endsWith(".drive.usercontent.google.com");
    }

    private String attribute(String value, String name) {
        Matcher matcher = Pattern.compile("(?is)\\b" + Pattern.quote(name) + "\\s*=\\s*([\"'])(.*?)\\1").matcher(value);
        return matcher.find() ? matcher.group(2) : null;
    }

    private String queryParam(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String htmlDecode(String value) {
        return value == null ? null : value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
