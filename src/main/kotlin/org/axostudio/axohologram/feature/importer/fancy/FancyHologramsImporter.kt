package org.axostudio.axohologram.feature.importer.fancy

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.text.ColorUtil
import org.axostudio.axohologram.common.validation.HologramId
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.line.BlockLine
import org.axostudio.axohologram.core.hologram.line.ItemLine
import org.axostudio.axohologram.core.hologram.visibility.VisibilityMode
import org.axostudio.axohologram.feature.importer.HologramImporter
import org.axostudio.axohologram.feature.importer.ImportResult
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Vector
import java.io.File
import java.util.Locale

/** Imports FancyHolograms' canonical `holograms.yml` without requiring its API. */
class FancyHologramsImporter(
    private val hologramService: HologramService,
    private val configManager: ConfigManager,
    pluginsFolder: File
) : HologramImporter {
    private val source = File(pluginsFolder, "FancyHolograms/holograms.yml")

    override fun id() = "fancy"
    override fun displayName() = "FancyHolograms"
    override fun isAvailable() = source.isFile

    override fun availableHolograms(): Collection<String> = root()?.getKeys(false)?.toList().orEmpty()

    override fun importHologram(name: String): ImportResult {
        val section = root()?.getKeys(false)?.firstOrNull { it.equals(name, true) }?.let { root()?.getConfigurationSection(it) }
        return if (section == null) ImportResult(id()).also { it.markFailed("FancyHolograms hologram '$name' was not found.") }
        else importSection(section.name ?: name, section)
    }

    override fun importAll(): ImportResult {
        val result = ImportResult(id())
        val sourceRoot = root() ?: return result.also { it.markFailed("FancyHolograms holograms.yml was not found or has no holograms section.") }
        sourceRoot.getKeys(false).forEach { name ->
            val imported = sourceRoot.getConfigurationSection(name)?.let { importSection(name, it) }
            when {
                imported == null -> result.markFailed("FancyHolograms hologram '$name' is invalid.")
                imported.imported > 0 -> result.markImported(imported.messages.firstOrNull() ?: "Imported '$name'.")
                imported.skipped > 0 -> result.markSkipped(imported.messages.firstOrNull() ?: "Skipped '$name'.")
                else -> result.markFailed(imported.messages.firstOrNull() ?: "Failed '$name'.")
            }
        }
        return result
    }

    private fun root(): ConfigurationSection? = if (source.isFile) YamlConfiguration.loadConfiguration(source).getConfigurationSection("holograms") else null

    private fun importSection(name: String, section: ConfigurationSection): ImportResult {
        val result = ImportResult(id())
        try {
            val location = location(section) ?: return result.also { it.markFailed("FancyHolograms '$name' has an unavailable world or location.") }
            val targetId = safeId(name)
            if (!prepareTarget(targetId, result)) return result
            val type = section.getString("type", "TEXT")!!.uppercase(Locale.ROOT)
            val hologram = when {
                type.contains("ITEM") -> createItem(targetId, location, section)
                type.contains("BLOCK") -> createBlock(targetId, location, section)
                else -> hologramService.create(targetId, location, textLines(section))
            }
            applyStyle(section, hologram)
            hologramService.update(hologram)
            hologramService.saveAll()
            result.markImported("Imported FancyHolograms '$name' as '$targetId'.")
        } catch (error: Exception) {
            result.markFailed("Could not import FancyHolograms '$name': ${error.message ?: error.javaClass.simpleName}")
        }
        return result
    }

    private fun createItem(id: String, location: Location, section: ConfigurationSection): Hologram {
        val stack = section.getItemStack("item")
        val raw = firstString(section, "material", "item", "content", "id")
        val material = stack?.type ?: Material.matchMaterial(raw ?: "") ?: throw IllegalArgumentException("missing item material")
        return hologramService.createItem(id, location, material.name, true).also { hologram ->
            stack?.let { (hologram.getPage(0).getLine(0) as? ItemLine)?.setItemStack(it) }
        }
    }

    private fun createBlock(id: String, location: Location, section: ConfigurationSection): Hologram {
        val raw = firstString(section, "block", "material", "content", "id") ?: "STONE"
        val data = runCatching { Bukkit.createBlockData(raw) }.getOrElse {
            val material = Material.matchMaterial(raw) ?: throw IllegalArgumentException("missing block material")
            require(material.isBlock) { "material is not a block" }
            material.createBlockData()
        }
        return hologramService.createBlock(id, location, data.asString, true).also { hologram ->
            (hologram.getPage(0).getLine(0) as? BlockLine)?.setBlockData(data)
        }
    }

    private fun textLines(section: ConfigurationSection): List<String> {
        val text = section.getStringList("text").ifEmpty { section.getStringList("lines") }
        val lines = text.ifEmpty { listOfNotNull(firstString(section, "text", "content", "line")) }.ifEmpty { listOf("") }
        return if (configManager.config.getBoolean("importer.import-placeholders", true)) lines else lines.map { it.replace(Regex("%[^%\\s]+%"), "") }
    }

    private fun location(section: ConfigurationSection): Location? {
        val world = Bukkit.getWorld(firstString(section, "location.world", "world") ?: return null) ?: return null
        return Location(world, section.getDouble("location.x"), section.getDouble("location.y"), section.getDouble("location.z"), section.getDouble("location.yaw").toFloat(), section.getDouble("location.pitch").toFloat())
    }

    private fun applyStyle(section: ConfigurationSection, hologram: Hologram) {
        hologram.isEnabled = section.getBoolean("enabled", true)
        firstString(section, "permission", "visibility.permission")?.takeIf { it.isNotBlank() }?.let { hologram.permission = it; hologram.visibilityMode = VisibilityMode.PERMISSION }
        firstString(section, "visibility", "visibility.mode")?.uppercase(Locale.ROOT)?.let { raw -> runCatching { VisibilityMode.valueOf(raw) }.getOrNull()?.let { hologram.visibilityMode = it } }
        firstInt(section, "visibility_distance", "visibility.distance", "view-distance", "view_distance")?.takeIf { it > 0 }?.let { hologram.viewDistance = it }
        val scale = section.getDouble("scale", 1.0).toFloat()
        hologram.setScale(section.getDouble("scale_x", section.getDouble("scale.x", scale.toDouble())).toFloat(), section.getDouble("scale_y", section.getDouble("scale.y", scale.toDouble())).toFloat(), section.getDouble("scale_z", section.getDouble("scale.z", scale.toDouble())).toFloat())
        if (section.contains("translation.x") || section.contains("translation.y") || section.contains("translation.z") || section.contains("offset.x")) hologram.offset = Vector(section.getDouble("translation.x", section.getDouble("offset.x")), section.getDouble("translation.y", section.getDouble("offset.y")), section.getDouble("translation.z", section.getDouble("offset.z")))
        firstString(section, "billboard")?.let { raw -> runCatching { Display.Billboard.valueOf(raw.uppercase(Locale.ROOT)) }.getOrNull()?.let { hologram.billboard = it } }
        if (section.contains("shadow_radius") || section.contains("shadow.radius")) hologram.shadowRadius = section.getDouble("shadow_radius", section.getDouble("shadow.radius")).toFloat()
        if (section.contains("shadow_strength") || section.contains("shadow.strength")) hologram.shadowStrength = section.getDouble("shadow_strength", section.getDouble("shadow.strength", 1.0)).toFloat()
        firstString(section, "background", "background-color", "background_color")?.let { runCatching { ColorUtil.parseColor(it) }.getOrNull()?.let { color -> hologram.backgroundColor = color } }
        if (section.contains("text_shadow")) hologram.setTextShadow(section.getBoolean("text_shadow"))
        if (section.contains("see_through")) hologram.isSeeThrough = section.getBoolean("see_through")
        firstString(section, "alignment", "text-alignment")?.let { raw -> runCatching { TextDisplay.TextAlignment.valueOf(raw.uppercase(Locale.ROOT)) }.getOrNull()?.let { hologram.alignment = it } }
        firstString(section, "display-animation", "display_animation", "animation.display", "animation")?.takeIf { configManager.config.getBoolean("importer.import-animations", true) }?.let { hologram.displayAnimation = it; hologram.isDisplayAnimationEnabled = true }
    }

    private fun prepareTarget(id: String, result: ImportResult): Boolean {
        if (!hologramService.exists(id)) return true
        if (!configManager.config.getBoolean("importer.overwrite-existing", false)) { result.markSkipped("Skipped '$id': a hologram with that ID already exists."); return false }
        hologramService.delete(id)
        return true
    }

    private fun safeId(raw: String): String = raw.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_', '-').take(64).let { if (HologramId.isValid(it)) it else "imported_hologram" }
    private fun firstString(section: ConfigurationSection, vararg paths: String): String? = paths.firstNotNullOfOrNull { path -> section.getString(path)?.takeIf { it.isNotBlank() } }
    private fun firstInt(section: ConfigurationSection, vararg paths: String): Int? = paths.firstNotNullOfOrNull { path -> section.takeIf { it.contains(path) }?.getInt(path) }
}
