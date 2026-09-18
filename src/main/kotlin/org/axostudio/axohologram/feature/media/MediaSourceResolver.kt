package org.axostudio.axohologram.feature.media

import java.io.File
import java.net.URI

internal object MediaSourceResolver {

    fun resolveLocal(mediaFolder: File, raw: String, fileUri: URI? = null): File {
        val root = mediaFolder.canonicalFile
        val source = if (fileUri != null) File(fileUri) else File(raw)
        val candidates = linkedSetOf<File>()

        if (source.isAbsolute) {
            candidates += source
        } else {
            candidates += File(root, raw)

            // Users commonly include the visible `media/` folder in commands,
            // even though mediaFolder already points at it. Accept both forms:
            // `image.png` and `media/image.png`.
            val normalized = raw.trim().replace('\\', '/').removePrefix("./")
            if (normalized.startsWith("media/", ignoreCase = true)) {
                candidates += File(root, normalized.substring("media/".length))
            }
        }

        val safeCandidates = candidates
            .map(File::getCanonicalFile)
            .filter { candidate ->
                candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
            }

        require(safeCandidates.isNotEmpty()) {
            "Local media must be inside '${root.path}'."
        }

        return safeCandidates.firstOrNull(File::isFile)
            ?: throw IllegalArgumentException(
                "Media file does not exist inside '${root.path}'."
            )
    }
}
