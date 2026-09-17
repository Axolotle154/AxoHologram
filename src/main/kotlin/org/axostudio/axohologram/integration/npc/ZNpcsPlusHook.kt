package org.axostudio.axohologram.integration.npc

import org.axostudio.axohologram.compatibility.npc.NpcBridge
import org.bukkit.Bukkit
import org.bukkit.Location
import java.lang.reflect.Modifier
import java.util.Optional

/** Uses the stable reflective surface because ZNPCsPlus changes packages between releases. */
class ZNpcsPlusHook : NpcBridge {

    override fun getProviderName(): String = "ZNPCsPlus"

    override fun isAvailable(): Boolean = pluginPresent() && api() != null

    override fun exists(npcIdentifier: String): Boolean = findNpc(npcIdentifier) != null

    override fun getLocation(npcIdentifier: String): Optional<Location> {
        val npc = findNpc(npcIdentifier) ?: return Optional.empty()
        return (call(npc, "getLocation") as? Location)?.let { Optional.of(it.clone()) } ?: Optional.empty()
    }

    override fun getNpcIdentifiers(): Collection<String> = allNpcs()
        .mapNotNull { npc ->
            (call(npc, "getId") ?: call(npc, "getName"))?.toString()?.takeIf(String::isNotBlank)
        }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

    private fun findNpc(identifier: String): Any? {
        val registry = registry() ?: return null
        call(registry, "getById", identifier).unwrapOptional()?.let { return it }
        call(registry, "getNpc", identifier).unwrapOptional()?.let { return it }
        return allNpcs().firstOrNull {
            val id = (call(it, "getId") ?: call(it, "getName"))?.toString()
            id.equals(identifier, true)
        }
    }

    private fun allNpcs(): Collection<Any> {
        val registry = registry() ?: return emptyList()
        val raw = call(registry, "getAll") ?: call(registry, "getNpcs") ?: call(registry, "values") ?: return emptyList()
        return when (raw) {
            is Map<*, *> -> raw.values.filterNotNull()
            is Iterable<*> -> raw.filterNotNull()
            is Array<*> -> raw.filterNotNull()
            else -> emptyList()
        }
    }

    private fun registry(): Any? = api()?.let { call(it, "getNpcRegistry") ?: call(it, "getNpcManager") ?: it }

    private fun api(): Any? {
        if (!pluginPresent()) return null
        val provider = sequenceOf(
            "lol.pyr.znpcsplus.api.NpcApiProvider",
            "lol.pyr.znpcsplus.api.ZNpcsPlusApi"
        ).mapNotNull { name -> runCatching { Class.forName(name) }.getOrNull() }.firstOrNull() ?: return null
        val method = provider.methods.firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) && candidate.parameterCount == 0 && candidate.name in setOf("get", "getApi", "getInstance")
        } ?: return null
        return runCatching { method.invoke(null) }.getOrNull().unwrapOptional()
    }

    private fun pluginPresent(): Boolean = listOf("ServersNPC", "ZNPCsPlus", "znpcs", "znpcsplus")
        .any(Bukkit.getPluginManager()::isPluginEnabled)

    private fun call(target: Any, name: String, vararg args: Any): Any? = runCatching {
        target.javaClass.methods.firstOrNull { method -> method.name == name && method.parameterCount == args.size }
            ?.invoke(target, *args)
    }.getOrNull()

    private fun Any?.unwrapOptional(): Any? = when (this) {
        is Optional<*> -> orElse(null)
        else -> this
    }
}
