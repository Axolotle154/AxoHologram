package org.axostudio.axohologram.core

import org.axostudio.axohologram.core.hologram.model.AxoHologram
import org.axostudio.axohologram.core.hologram.model.HologramPosition
import org.axostudio.axohologram.persistence.yaml.YamlHologramStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class HologramGroupStorageTest {

    @Test
    fun `load report keeps valid holograms and identifies malformed files`(@TempDir tempDir: Path) {
        val root = tempDir.resolve("holograms").toFile().also { it.mkdirs() }
        File(root, "valid.yml").writeText(
            "location:\n  world: world\n  x: 0.0\n  y: 64.0\n  z: 0.0\nlines:\n  - Welcome\n"
        )
        File(root, "broken.yml").writeText("location: [unterminated\n")

        val report = YamlHologramStorage(root).loadAllWithReport()

        assertEquals(listOf("valid"), report.holograms.map { it.id })
        assertEquals(1, report.failures.size)
        assertEquals("broken", report.failures.single().id)
        assertEquals("broken.yml", report.failures.single().source)
    }

    @Test
    fun testSubfolderSaveAndLoad(@TempDir tempDir: Path) {
        val root = tempDir.resolve("holograms").toFile()
        root.mkdirs()

        val storage = YamlHologramStorage(root)

        // 1. Create a hologram with a group
        val pvpPos = HologramPosition("world", 10.0, 64.0, 20.0, 0f, 0f)
        val pvpHolo = AxoHologram("top_kills", pvpPos)
        pvpHolo.group = "pvp"
        pvpHolo.addLine("Top Kills")

        storage.save(pvpHolo)

        // Check that file was saved inside the "pvp" subfolder
        val pvpFile = File(File(root, "pvp"), "top_kills.yml")
        assertTrue(pvpFile.exists(), "File should be created in subfolder holograms/pvp/")

        // 2. Load it back
        val loaded = storage.load("top_kills")
        assertNotNull(loaded)
        assertEquals("top_kills", loaded!!.id)
        assertEquals("pvp", loaded.group)

        // 3. Move hologram to root ("root" or empty)
        loaded.group = "root"
        storage.save(loaded)

        val rootFile = File(root, "top_kills.yml")
        assertTrue(rootFile.exists(), "File should now exist in root folder")
        assertFalse(pvpFile.exists(), "Old subfolder file should be deleted upon moving")

        // 4. Test auto-detection of group from subfolder name if yaml has no group
        val lobbyFolder = File(root, "lobby")
        lobbyFolder.mkdirs()
        val manualFile = File(lobbyFolder, "welcome.yml")
        manualFile.writeText("location:\n  world: world\n  x: 0.0\n  y: 64.0\n  z: 0.0\nlines:\n  - text: 'Welcome'\n")

        val all = storage.loadAll()
        val welcomeHolo = all.find { it.id == "welcome" }
        assertNotNull(welcomeHolo)
        assertEquals("lobby", welcomeHolo!!.group, "Hologram should infer group 'lobby' from parent folder")
    }
}
