package org.axostudio.axohologram.media;

import java.io.File;
import java.net.URI;

public record MediaDownloadResult(
        URI source,
        File file,
        MediaType mediaType,
        String mimeType,
        String extension,
        long bytes,
        String cacheKey
) {
}
