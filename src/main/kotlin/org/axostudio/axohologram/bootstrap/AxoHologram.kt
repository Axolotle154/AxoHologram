package org.axostudio.axohologram.bootstrap

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.HologramManager
import org.bukkit.plugin.java.JavaPlugin

class AxoHologram : JavaPlugin() {

    companion object {
        @JvmStatic
        lateinit var instance: AxoHologram
            private set
    }

    val lifecycle: PluginLifecycle by lazy { PluginLifecycle(this) }

    val serviceRegistry: ServiceRegistry
        get() = lifecycle.registry

    val hologramService: HologramService?
        get() = serviceRegistry.get()

    val hologramManager: HologramManager?
        get() = serviceRegistry.get()

    val configManager: ConfigManager?
        get() = serviceRegistry.get()

    override fun onLoad() {
        instance = this
    }

    override fun onEnable() {
        lifecycle.enable()
    }

    override fun onDisable() {
        lifecycle.disable()
    }
}
