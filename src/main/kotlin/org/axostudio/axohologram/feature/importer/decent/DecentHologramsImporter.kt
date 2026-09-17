package org.axostudio.axohologram.feature.importer.decent

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.validation.HologramId
import org.axostudio.axohologram.core.hologram.model.AxoHologramPage
import org.axostudio.axohologram.core.hologram.visibility.VisibilityMode
import org.axostudio.axohologram.feature.importer.HologramImporter
import org.axostudio.axohologram.feature.importer.ImportResult
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.util.Vector
import java.io.File
import java.util.Locale

class DecentHologramsImporter(
    private val hologramService: HologramService,
    private val pluginsFolder: File = File("plugins")
) : HologramImporter {

    private val dhFolder = File(pluginsFolder, "DecentHolograms/holograms")

    override fun id(): String = "decentholograms"
    override fun displayName(): String = "DecentHolograms"

    override fun isAvailable(): Boolean = dhFolder.exists() && dhFolder.isDirectory

    override fun availableHolograms(): Collection<String> {
        if (!isAvailable()) return emptyList()
        return dhFolder.listFiles { file -> file.isFile && file.name.endsWith(".yml") }
            ?.map { it.name.removeSuffix(".yml") }
            ?: emptyList()
    }

    override fun importHologram(name: String): ImportResult {
        val result = ImportResult(id())
        val file = File(dhFolder, "$name.yml")
        if (!file.exists()) {
            result.markFailed("File not found: ${file.name}")
            return result
        }

        try {
            val yaml = YamlConfiguration.loadConfiguration(file)
            val worldName = yaml.getString("location.world") ?: "world"
            val world = Bukkit.getWorld(worldName) ?: Bukkit.getWorlds().firstOrNull()
            val x = yaml.getDouble("location.x", 0.0)
            val y = yaml.getDouble("location.y", 0.0)
            val z = yaml.getDouble("location.z", 0.0)
            val loc = if (world != null) Location(world, x, y, z) else null

            if (loc == null) {
                result.markFailed("Could not resolve world for: $name")
                return result
            }

            val id = yaml.getString("name")?.takeIf(String::isNotBlank) ?: name
            HologramId.requireValid(id, "Imported hologram id")
            val pages = readPages(yaml)
            val holo = hologramService.create(id, loc, pages.firstOrNull().orEmpty())
            pages.drop(1).forEachIndexed { index, lines ->
                holo.addPage(AxoHologramPage(index + 1).also { page -> lines.forEach(page::addLine) })
            }
            applySettings(yaml, holo)
            hologramService.saveAll()
            result.markImported("Successfully imported hologram $id with ${pages.sumOf { it.size }} lines across ${pages.size} pages")
        } catch (e: Exception) {
            result.markFailed("Error importing $name: ${e.message}")
        }
        return result
    }

    override fun importAll(): ImportResult {
        val result = ImportResult(id())
        for (name in availableHolograms()) {
            val single = importHologram(name)
            if (single.imported > 0) result.markImported("Imported $name")
            else if (single.skipped > 0) result.markSkipped("Skipped $name")
            else result.markFailed("Failed $name")
        }
        return result
    }

    /** Supports both current DH `pages` structures and older flat `lines` files. */
    private fun readPages(yaml: YamlConfiguration): List<List<String>> {
        val pages = mutableListOf<List<String>>()
        when (val raw = yaml.get("pages")) {
            is List<*> -> raw.mapNotNullTo(pages, ::linesFrom)
            is ConfigurationSection -> raw.getKeys(false).sortedWith(naturalKeyOrder).mapNotNullTo(pages) { key -> linesFrom(raw.get(key)) }
        }
        yaml.getConfigurationSection("pages")?.let { section ->
            if (pages.isEmpty()) section.getKeys(false).sortedWith(naturalKeyOrder).mapNotNullTo(pages) { key -> linesFrom(section.get(key)) }
        }
        if (pages.isEmpty()) pages += yaml.getStringList("lines")
        return if (pages.isEmpty()) listOf(emptyList()) else pages
    }

    private fun linesFrom(raw: Any?): List<String>? = when (raw) {
        is String -> listOf(raw)
        is List<*> -> raw.mapNotNull { it?.toString() }
        is Map<*, *> -> linesFrom(raw["lines"] ?: raw["text"] ?: raw["content"])
        is ConfigurationSection -> linesFrom(raw.get("lines") ?: raw.get("text") ?: raw.get("content"))
        else -> null
    }?.takeIf { it.isNotEmpty() }

    private fun applySettings(yaml: YamlConfiguration, hologram: org.axostudio.axohologram.api.hologram.Hologram) {
        hologram.isEnabled = yaml.getBoolean("enabled", true)
        yaml.getString("permission")?.takeIf(String::isNotBlank)?.let {
            hologram.permission = it
            hologram.visibilityMode = VisibilityMode.PERMISSION
        }
        val distance = listOf("visibility-distance", "display-range", "view-distance", "range")
            .firstNotNullOfOrNull { key -> yaml.takeIf { it.contains(key) }?.getInt(key) }
        if (distance != null && distance > 0) hologram.viewDistance = distance
        val uniformScale = yaml.getDouble("scale", Double.NaN)
        val scaleX = yaml.getDouble("scale.x", if (uniformScale.isNaN()) 1.0 else uniformScale).toFloat()
        val scaleY = yaml.getDouble("scale.y", if (uniformScale.isNaN()) 1.0 else uniformScale).toFloat()
        val scaleZ = yaml.getDouble("scale.z", if (uniformScale.isNaN()) 1.0 else uniformScale).toFloat()
        hologram.setScale(scaleX, scaleY, scaleZ)
        val billboard = yaml.getString("billboard")?.let { raw -> runCatching { Display.Billboard.valueOf(raw.uppercase(Locale.ROOT)) }.getOrNull() }
        if (billboard != null) hologram.billboard = billboard
        if (yaml.contains("offset.x") || yaml.contains("offset.y") || yaml.contains("offset.z")) {
            hologram.offset = Vector(yaml.getDouble("offset.x"), yaml.getDouble("offset.y"), yaml.getDouble("offset.z"))
        }
    }

    private val naturalKeyOrder = Comparator<String> { first, second ->
        first.toIntOrNull()?.let { left -> second.toIntOrNull()?.let { right -> return@Comparator left.compareTo(right) } }
        first.compareTo(second, ignoreCase = true)
    }
}
