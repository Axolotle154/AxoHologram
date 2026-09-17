package org.axostudio.axohologram.integration.npc

import de.oliver.fancynpcs.api.FancyNpcsPlugin
import de.oliver.fancynpcs.api.Npc
import org.axostudio.axohologram.compatibility.npc.NpcBridge
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.Optional

class FancyNpcsHook : NpcBridge {

    override fun getProviderName(): String = "FancyNpcs"

    override fun isAvailable(): Boolean {
        return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs") &&
                runCatching { FancyNpcsPlugin.get().npcManager != null }.getOrDefault(false)
    }

    override fun exists(npcIdentifier: String): Boolean {
        return findNpc(npcIdentifier) != null
    }

    override fun getLocation(npcIdentifier: String): Optional<Location> {
        val npc = findNpc(npcIdentifier) ?: return Optional.empty()
        return Optional.ofNullable(npc.data.location)
    }

    override fun getNpcIdentifiers(): Collection<String> {
        if (!isAvailable()) return emptyList()
        return runCatching {
            FancyNpcsPlugin.get().npcManager.allNpcs.map { it.data.name }
        }.getOrDefault(emptyList())
    }

    private fun findNpc(name: String): Npc? {
        if (!isAvailable()) return null
        return runCatching {
            val manager = FancyNpcsPlugin.get().npcManager
            manager.getNpc(name) ?: manager.allNpcs.firstOrNull { it.data.name.equals(name, ignoreCase = true) }
        }.getOrNull()
    }
}
