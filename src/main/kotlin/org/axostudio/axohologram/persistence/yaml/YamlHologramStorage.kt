package org.axostudio.axohologram.persistence.yaml

import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.common.validation.HologramId
import org.axostudio.axohologram.persistence.HologramStorage
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.Locale

class YamlHologramStorage(val baseFolder: File) : HologramStorage {

    init {
        if (!baseFolder.exists()) {
            baseFolder.mkdirs()
        }
    }

    override fun loadAll(): Collection<Hologram> {
        val list = mutableListOf<Hologram>()
        val files = mutableListOf<File>()
        collectYamlFiles(baseFolder, files)

        for (file in files) {
            val id = file.name.removeSuffix(".yml")
            try {
                val yaml = YamlConfiguration.loadConfiguration(file)
                // 3.x stored image/video holograms beside ordinary holograms.
                // MediaManager imports them separately into the media runtime.
                if (yaml.getString("type")?.uppercase() in setOf("IMAGE", "VIDEO") && yaml.isSet("url")) continue
                val holo = HologramDeserializer.deserialize(id, yaml)
                list.add(holo)
            } catch (e: Exception) {
                // Log error
            }
        }
        return list
    }

    override fun load(id: String): Hologram? {
        if (!HologramId.isValid(id)) return null
        val file = safeFile(baseFolder, id) ?: return null
        if (!file.exists()) return null
        return try {
            val yaml = YamlConfiguration.loadConfiguration(file)
            HologramDeserializer.deserialize(id, yaml)
        } catch (e: Exception) {
            null
        }
    }

    override fun save(hologram: Hologram) {
        HologramId.requireValid(hologram.id)
        if (!hologram.isPersistent) {
            delete(hologram.id)
            return
        }
        // A group change moves the file. Remove any previous copy first so reload
        // cannot resurrect a stale duplicate under the old group directory.
        delete(hologram.id)
        val targetFolder = if (hologram.group.isNotBlank()) {
            HologramId.requireValid(hologram.group, "Group id")
            File(baseFolder, hologram.group).also { if (!it.exists()) it.mkdirs() }
        } else {
            baseFolder
        }

        val file = safeFile(targetFolder, hologram.id)
            ?: throw IllegalArgumentException("Invalid hologram storage path")
        val yaml = HologramSerializer.serialize(hologram)
        yaml.save(file)
    }

    override fun saveAll(holograms: Collection<Hologram>) {
        for (h in holograms) {
            save(h)
        }
    }

    override fun delete(id: String): Boolean {
        if (!HologramId.isValid(id)) return false
        val files = mutableListOf<File>()
        collectYamlFiles(baseFolder, files)
        val target = files.firstOrNull { it.name.equals("$id.yml", ignoreCase = true) }
        return target?.delete() ?: false
    }

    private fun collectYamlFiles(dir: File, accumulator: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                collectYamlFiles(f, accumulator)
            } else if (f.name.lowercase(Locale.ROOT).endsWith(".yml")) {
                accumulator.add(f)
            }
        }
    }

    private fun safeFile(folder: File, id: String): File? {
        if (!HologramId.isValid(id)) return null
        val root = folder.canonicalFile
        val target = File(root, "$id.yml").canonicalFile
        return target.takeIf { it.parentFile == root }
    }
}
