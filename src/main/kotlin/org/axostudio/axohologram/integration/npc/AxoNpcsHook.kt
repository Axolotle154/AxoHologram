package org.axostudio.axohologram.integration.npc

import org.axostudio.axohologram.compatibility.npc.NpcBridge
import org.axostudio.axonpcs.api.AxoNPCsProvider
import org.axostudio.axonpcs.api.service.AxoNPCProvider
import org.axostudio.axonpcs.api.service.AxoNPCService
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.Optional

/** Direct bridge for the AxoNPCs dependency declared by the plugin descriptor. */
class AxoNpcsHook : NpcBridge {
    override fun getProviderName(): String = "AxoNPCs"

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().isPluginEnabled("AxoNPCs") &&
        (runCatching { AxoNPCProvider.get() != null }.getOrDefault(false) ||
         runCatching { AxoNPCsProvider.isAvailable() }.getOrDefault(false))

    override fun exists(npcIdentifier: String): Boolean = service()?.exists(npcIdentifier) ?: false

    override fun getLocation(npcIdentifier: String): Optional<Location> = service()
        ?.getNPC(npcIdentifier)
        ?.map { it.location.clone() }
        ?: Optional.empty()

    override fun getNpcIdentifiers(): Collection<String> = service()
        ?.allNPCs
        ?.mapNotNull { it.id?.takeIf(String::isNotBlank) }
        ?.sortedBy { it.lowercase() }
        ?: emptyList()

    private fun service(): AxoNPCService? {
        if (!Bukkit.getPluginManager().isPluginEnabled("AxoNPCs")) return null
        return runCatching { AxoNPCProvider.get() }.getOrNull()
            ?: runCatching { AxoNPCsProvider.getAPI() }.getOrNull()
    }
}
