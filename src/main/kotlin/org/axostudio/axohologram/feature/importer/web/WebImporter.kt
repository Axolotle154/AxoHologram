package org.axostudio.axohologram.feature.importer.web

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.axostudio.axohologram.common.validation.HologramId
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.HologramManager
import org.axostudio.axohologram.feature.importer.HologramImporter
import org.axostudio.axohologram.feature.importer.ImportResult
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.axostudio.axohologram.persistence.yaml.HologramDeserializer
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration

class WebImporter(
    private val hologramManager: HologramManager,
    private val configManager: ConfigManager,
    private val scheduler: TaskScheduler,
    private val client: WebImportClient = WebImportClient {
        configManager.config.getString("importer.web-api-url", WebImportClient.DEFAULT_API_URL)
            ?: WebImportClient.DEFAULT_API_URL
    }
) : HologramImporter {

    override fun id(): String = "web"
    override fun displayName(): String = "AxoStudio Web Importer"
    override fun isAvailable(): Boolean = configManager.config.getBoolean("importer.enabled", true)
    override fun availableHolograms(): Collection<String> = emptyList()

    /** Downloads off-thread and creates the hologram on the global scheduler. */
    fun importFromWebAsync(
        codeOrUrl: String,
        customTargetId: String?,
        targetLocation: Location?,
        completed: (ImportResult) -> Unit
    ) {
        scheduler.runAsync(Runnable {
            val fetched = client.fetchHologram(codeOrUrl)
            scheduler.runGlobal(Runnable {
                completed(importFetched(fetched, codeOrUrl, customTargetId, targetLocation))
            })
        })
    }

    override fun importHologram(name: String): ImportResult =
        importFetched(client.fetchHologram(name), name, null, null)

    override fun importAll(): ImportResult = ImportResult(id()).also {
        it.markSkipped("Bulk web import is not supported; provide a share code or URL.")
    }

    private fun importFetched(
        fetched: WebFetchResult,
        codeOrUrl: String,
        customTargetId: String?,
        targetLocation: Location?
    ): ImportResult {
        val result = ImportResult(id())
        val body = fetched.body
        if (body == null) {
            result.markFailed(fetched.error ?: "Failed to fetch hologram from: $codeOrUrl")
            return result
        }
        try {
            val yaml = decodePayload(body, codeOrUrl)
            val requestedId = customTargetId?.takeIf { it.isNotBlank() }
                ?: yaml.getString("id")
                ?: codeFromInput(codeOrUrl)
            val targetId = resolveTargetId(requestedId)
            targetLocation?.let { location ->
                yaml.set("location.world", location.world.name)
                yaml.set("location.x", location.x)
                yaml.set("location.y", location.y)
                yaml.set("location.z", location.z)
                yaml.set("location.yaw", location.yaw)
                yaml.set("location.pitch", location.pitch)
            }
            val hologram = HologramDeserializer.deserialize(targetId, yaml)
            if (hologramManager.exists(targetId)) {
                if (!configManager.config.getBoolean("importer.overwrite-existing", false)) {
                    result.markFailed("A hologram with ID '$targetId' already exists.")
                    return result
                }
                hologramManager.delete(targetId)
            }
            hologramManager.registerCreatedHologram(hologram)
            result.markImported("Imported '$targetId' from ${fetched.url ?: "web"}.")
        } catch (exception: Exception) {
            result.markFailed("Could not import web hologram: ${exception.message ?: exception.javaClass.simpleName}")
        }
        return result
    }

    private fun decodePayload(body: String, codeOrUrl: String): YamlConfiguration {
        val yaml = YamlConfiguration()
        if (body.trimStart().startsWith("{")) {
            val root = JsonParser.parseString(body).asJsonObject
            // The public AxoStudio endpoint returns the same pages/lines schema
            // used by YAML exports. Preserve every field instead of collapsing it
            // to the obsolete {id, world, x, y, z, lines} model.
            val hologram = root.getAsJsonObject("hologram") ?: root
            hologram.entrySet().forEach { (key, value) -> yaml.set(key, value.toConfigValue()) }
            if (yaml.getString("id").isNullOrBlank()) yaml.set("id", codeFromInput(codeOrUrl))
        } else yaml.loadFromString(body)
        return yaml
    }

    private fun resolveTargetId(rawId: String): String {
        val safeBase = rawId.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_', '-').take(64).ifBlank { "imported_hologram" }
            .let { if (HologramId.isValid(it)) it else "imported_hologram" }
        if (configManager.config.getBoolean("importer.overwrite-existing", false) || !hologramManager.exists(safeBase)) return safeBase
        var index = 1
        var candidate = "${safeBase.take(54)}_imported"
        while (hologramManager.exists(candidate)) candidate = "${safeBase.take(50)}_imported_${++index}"
        return candidate
    }

    private fun codeFromInput(value: String): String = value.substringBefore('?').substringAfterLast('/').ifBlank { "web_hologram" }
}

private fun JsonElement.toConfigValue(): Any? = when {
    isJsonNull -> null
    isJsonPrimitive -> {
        val primitive = asJsonPrimitive
        when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asNumber
            else -> primitive.asString
        }
    }
    isJsonArray -> asJsonArray.map { it.toConfigValue() }
    isJsonObject -> asJsonObject.entrySet().associate { entry -> entry.key to entry.value.toConfigValue() }
    else -> null
}
