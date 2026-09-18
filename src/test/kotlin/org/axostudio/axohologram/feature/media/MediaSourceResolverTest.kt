package org.axostudio.axohologram.feature.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MediaSourceResolverTest {

    @Test
    fun `accepts paths with or without the media folder prefix`(@TempDir tempDir: Path) {
        val mediaFolder = tempDir.resolve("media")
        Files.createDirectories(mediaFolder)
        val image = mediaFolder.resolve("uhc_agacharse.png")
        Files.write(image, byteArrayOf(1, 2, 3))

        assertEquals(
            image.toFile().canonicalFile,
            MediaSourceResolver.resolveLocal(mediaFolder.toFile(), "uhc_agacharse.png")
        )
        assertEquals(
            image.toFile().canonicalFile,
            MediaSourceResolver.resolveLocal(mediaFolder.toFile(), "media/uhc_agacharse.png")
        )
    }
}
