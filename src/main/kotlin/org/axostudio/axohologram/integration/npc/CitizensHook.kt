package org.axostudio.axohologram.integration.npc

import net.citizensnpcs.api.CitizensAPI
import org.axostudio.axohologram.compatibility.npc.NpcBridge
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.Optional

class CitizensHook : NpcBridge {

    override fun getProviderName(): String = "Citizens"

    override fun isAvailable(): Boolean {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens")
    }

    override fun exists(npcIdentifier: String): Boolean {
        if (!isAvailable()) return false
        val id = npcIdentifier.toIntOrNull()
        if (id != null) {
            return CitizensAPI.getNPCRegistry().getById(id) != null
        }
        return CitizensAPI.getNPCRegistry().any { it.name.equals(npcIdentifier, ignoreCase = true) }
    }

    override fun getLocation(npcIdentifier: String): Optional<Location> {
        if (!isAvailable()) return Optional.empty()
        val id = npcIdentifier.toIntOrNull()
        val npc = if (id != null) {
            CitizensAPI.getNPCRegistry().getById(id)
        } else {
            CitizensAPI.getNPCRegistry().firstOrNull { it.name.equals(npcIdentifier, ignoreCase = true) }
        }
        return Optional.ofNullable(npc?.storedLocation)
    }

    override fun getNpcIdentifiers(): Collection<String> {
        if (!isAvailable()) return emptyList()
        return CitizensAPI.getNPCRegistry().map { it.id.toString() }
    }
}
