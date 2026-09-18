package org.axostudio.axohologram.integration.npc

import org.axostudio.axohologram.compatibility.npc.NpcBridge
import org.bukkit.Bukkit
import org.bukkit.Location
import java.lang.reflect.Modifier
import java.util.Optional

/**
 * Optional AxoNPCs bridge. Reflection deliberately keeps AxoNPCs out of this
 * plugin's compile classpath, so servers and JitPack builds do not require it.
 */
class AxoNpcsHook : NpcBridge {
    override fun getProviderName(): String = "AxoNPCs"

    override fun isAvailable(): Boolean = pluginPresent() && service() != null

    override fun exists(npcIdentifier: String): Boolean = findNpc(npcIdentifier) != null

    override fun getLocation(npcIdentifier: String): Optional<Location> {
        val npc = findNpc(npcIdentifier) ?: return Optional.empty()
        return (call(npc, "getLocation") as? Location)?.let { Optional.of(it.clone()) } ?: Optional.empty()
    }

    override fun getNpcIdentifiers(): Collection<String> = allNpcs()
        .mapNotNull { npc -> call(npc, "getId")?.toString()?.takeIf(String::isNotBlank) }
        .distinctBy(String::lowercase)
        .sortedBy(String::lowercase)

    private fun findNpc(identifier: String): Any? {
        val service = service() ?: return null
        call(service, "getNPC", identifier).unwrapOptional()?.let { return it }
        call(service, "getNpc", identifier).unwrapOptional()?.let { return it }
        return allNpcs().firstOrNull { npc ->
            call(npc, "getId")?.toString()?.equals(identifier, ignoreCase = true) == true
        }
    }

    private fun allNpcs(): Collection<Any> {
        val service = service() ?: return emptyList()
        val raw = call(service, "getAllNPCs")
            ?: call(service, "getNPCs")
            ?: call(service, "getAllNpcs")
            ?: call(service, "getNpcs")
            ?: return emptyList()
        return when (raw) {
            is Map<*, *> -> raw.values.filterNotNull()
            is Iterable<*> -> raw.filterNotNull()
            is Array<*> -> raw.filterNotNull()
            else -> emptyList()
        }
    }

    private fun service(): Any? {
        if (!pluginPresent()) return null
        return staticFactory("org.axostudio.axonpcs.api.service.AxoNPCProvider", "get")
            ?: staticFactory("org.axostudio.axonpcs.api.AxoNPCsProvider", "getAPI", "get")
    }

    private fun staticFactory(className: String, vararg methodNames: String): Any? = runCatching {
        val provider = Class.forName(className)
        provider.methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) && method.parameterCount == 0 && method.name in methodNames
        }?.invoke(null).unwrapOptional()
    }.getOrNull()

    private fun pluginPresent(): Boolean = Bukkit.getPluginManager().isPluginEnabled("AxoNPCs")

    private fun call(target: Any, name: String, vararg args: Any): Any? = runCatching {
        target.javaClass.methods.firstOrNull { method -> method.name == name && method.parameterCount == args.size }
            ?.invoke(target, *args)
    }.getOrNull()

    private fun Any?.unwrapOptional(): Any? = when (this) {
        is Optional<*> -> orElse(null)
        else -> this
    }
}
