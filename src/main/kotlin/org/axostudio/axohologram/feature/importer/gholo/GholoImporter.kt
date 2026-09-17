package org.axostudio.axohologram.feature.importer.gholo

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.text.ColorUtil
import org.axostudio.axohologram.common.validation.HologramId
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.line.TextLine
import org.axostudio.axohologram.core.hologram.visibility.VisibilityMode
import org.axostudio.axohologram.feature.importer.HologramImporter
import org.axostudio.axohologram.feature.importer.ImportResult
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.util.Vector
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Locale

/** Reads GHolo's SQLite database directly, including rows, symbols and display style. */
class GholoImporter(
    private val hologramService: HologramService,
    private val configManager: ConfigManager,
    pluginsFolder: File
) : HologramImporter {
    private val folder = File(pluginsFolder, "GHolo")
    private val database = File(folder, "data/data.db")
    private val configFile = File(folder, "config.yml")

    override fun id() = "gholo"
    override fun displayName() = "GHolo"
    override fun isAvailable() = database.isFile && sqliteAvailable()
    override fun availableHolograms(): Collection<String> = records().map { it.name }

    override fun importHologram(name: String): ImportResult = records().firstOrNull { it.name.equals(name, true) }
        ?.let(::importRecord) ?: ImportResult(id()).also { it.markFailed("GHolo hologram '$name' was not found.") }

    override fun importAll(): ImportResult {
        val result = ImportResult(id())
        if (!isAvailable()) return result.also { it.markFailed("GHolo data.db was not found or the SQLite driver is unavailable.") }
        records().forEach { record ->
            val imported = importRecord(record)
            when {
                imported.imported > 0 -> result.markImported(imported.messages.firstOrNull() ?: "Imported '${record.name}'.")
                imported.skipped > 0 -> result.markSkipped(imported.messages.firstOrNull() ?: "Skipped '${record.name}'.")
                else -> result.markFailed(imported.messages.firstOrNull() ?: "Failed '${record.name}'.")
            }
        }
        return result
    }

    private fun importRecord(record: Record): ImportResult {
        val result = ImportResult(id())
        try {
            val locationJson = json(record.location)
            val worldName = string(locationJson, "world", "worldName", "world-name") ?: return result.also { it.markFailed("GHolo '${record.name}' has no world.") }
            val world = Bukkit.getWorld(worldName) ?: return result.also { it.markFailed("GHolo '${record.name}' uses unavailable world '$worldName'.") }
            val location = Location(world, number(locationJson, 0.0, "x"), number(locationJson, 0.0, "y"), number(locationJson, 0.0, "z"), number(locationJson, 0.0, "yaw").toFloat(), number(locationJson, 0.0, "pitch").toFloat())
            val targetId = safeId(record.name)
            if (!prepareTarget(targetId, result)) return result
            val rows = readRows(record.uuid, symbols())
            val hologram = hologramService.create(targetId, location, rows.map { it.text }.ifEmpty { listOf("") })
            rows.forEachIndexed { index, row ->
                (hologram.getPage(0).getLine(index) as? TextLine)?.apply { offset = row.offset; permission = row.permission }
            }
            applyStyle(json(record.data), hologram)
            hologramService.update(hologram)
            hologramService.saveAll()
            result.markImported("Imported GHolo '${record.name}' as '$targetId'.")
        } catch (error: Exception) {
            result.markFailed("Could not import GHolo '${record.name}': ${error.message ?: error.javaClass.simpleName}")
        }
        return result
    }

    private fun records(): List<Record> {
        if (!isAvailable()) return emptyList()
        return runCatching {
            connection().use { db ->
                db.createStatement().use { statement -> statement.executeQuery("SELECT uuid, id, location, data FROM gholo_holo ORDER BY id COLLATE NOCASE").use { rows ->
                    buildList { while (rows.next()) add(Record(rows.getString("uuid"), rows.getString("id"), rows.getString("location"), rows.getString("data"))) }
                } }
            }
        }.getOrDefault(emptyList())
    }

    private fun readRows(uuid: String, symbols: Map<String, String>): List<Row> = connection().use { db ->
        db.prepareStatement("SELECT position, content, `offset`, data FROM gholo_holo_row WHERE holo_uuid = ? ORDER BY position").use { statement ->
            statement.setString(1, uuid)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val offset = json(rows.getString("offset"))
                        val data = json(rows.getString("data"))
                        val content = symbols.entries.fold(rows.getString("content") ?: "") { text, symbol -> text.replace(symbol.key, symbol.value) }
                        val text = if (configManager.config.getBoolean("importer.import-placeholders", true)) content else content.replace(Regex("%[^%\\s]+%"), "")
                        add(Row(text, Vector(number(offset, 0.0, "x"), 0.0, number(offset, 0.0, "z")), string(data, "permission", "viewPermission", "view-permission")))
                    }
                }
            }
        }
    }

    private fun applyStyle(data: JsonObject, hologram: Hologram) {
        hologram.isEnabled = bool(data, true, "enabled", "visible")
        integer(data, -1, "visibilityDistance", "visibility-distance", "viewDistance", "view-distance", "range").takeIf { it > 0 }?.let { hologram.viewDistance = it }
        val scale = number(data, 1.0, "scale").toFloat()
        hologram.setScale(number(data, scale.toDouble(), "scaleX", "scale-x").toFloat(), number(data, scale.toDouble(), "scaleY", "scale-y").toFloat(), number(data, scale.toDouble(), "scaleZ", "scale-z").toFloat())
        string(data, "billboard")?.let { raw -> runCatching { Display.Billboard.valueOf(raw.uppercase(Locale.ROOT)) }.getOrNull()?.let { hologram.billboard = it } }
        hologram.shadowRadius = number(data, 0.0, "shadowRadius", "shadow-radius").toFloat()
        hologram.shadowStrength = number(data, 1.0, "shadowStrength", "shadow-strength").toFloat()
        string(data, "background", "backgroundColor", "background-color", "textBackground", "text-background")?.let { raw -> runCatching { ColorUtil.parseColor(raw) }.getOrNull()?.let { hologram.backgroundColor = it } }
        hologram.setTextShadow(bool(data, false, "textShadow", "text-shadow"))
        hologram.isSeeThrough = bool(data, false, "seeThrough", "see-through")
        string(data, "alignment", "textAlignment", "text-alignment")?.let { raw -> runCatching { TextDisplay.TextAlignment.valueOf(raw.uppercase(Locale.ROOT)) }.getOrNull()?.let { hologram.alignment = it } }
        string(data, "displayAnimation", "display-animation", "animation")?.takeIf { configManager.config.getBoolean("importer.import-animations", true) }?.let { hologram.displayAnimation = it; hologram.isDisplayAnimationEnabled = true }
    }

    private fun symbols(): Map<String, String> {
        if (!configFile.isFile) return emptyMap()
        val section = YamlConfiguration.loadConfiguration(configFile).getConfigurationSection("Options.Symbols") ?: return emptyMap()
        return section.getKeys(false).mapNotNull { key -> section.getString(key)?.let { key to it } }.toMap()
    }

    private fun prepareTarget(id: String, result: ImportResult): Boolean {
        if (!hologramService.exists(id)) return true
        if (!configManager.config.getBoolean("importer.overwrite-existing", false)) { result.markSkipped("Skipped '$id': a hologram with that ID already exists."); return false }
        hologramService.delete(id)
        return true
    }

    private fun sqliteAvailable(): Boolean = runCatching { Class.forName("org.sqlite.JDBC") }.isSuccess
    private fun connection(): Connection = DriverManager.getConnection("jdbc:sqlite:${database.absolutePath.replace('\\', '/')}")
    private fun safeId(raw: String): String = raw.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_', '-').take(64).let { if (HologramId.isValid(it)) it else "imported_hologram" }
    private fun json(raw: String?): JsonObject = runCatching { JsonParser.parseString(raw ?: "{}").takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject() }.getOrDefault(JsonObject())
    private fun value(object_: JsonObject, keys: Array<out String>): JsonElement? = keys.firstNotNullOfOrNull { object_.get(it)?.takeUnless(JsonElement::isJsonNull) }
    private fun string(object_: JsonObject, vararg keys: String): String? = runCatching { value(object_, keys)?.asString?.takeIf { it.isNotBlank() } }.getOrNull()
    private fun number(object_: JsonObject, fallback: Double, vararg keys: String): Double = runCatching { value(object_, keys)?.asDouble ?: fallback }.getOrDefault(fallback)
    private fun integer(object_: JsonObject, fallback: Int, vararg keys: String): Int = runCatching { value(object_, keys)?.asInt ?: fallback }.getOrDefault(fallback)
    private fun bool(object_: JsonObject, fallback: Boolean, vararg keys: String): Boolean = runCatching { value(object_, keys)?.asBoolean ?: fallback }.getOrDefault(fallback)

    private data class Record(val uuid: String, val name: String, val location: String?, val data: String?)
    private data class Row(val text: String, val offset: Vector, val permission: String?)
}
