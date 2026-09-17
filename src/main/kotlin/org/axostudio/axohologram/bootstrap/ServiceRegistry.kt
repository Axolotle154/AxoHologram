package org.axostudio.axohologram.bootstrap

import org.axostudio.axohologram.api.AxoHologramProvider
import org.axostudio.axohologram.api.hologram.HologramService
import java.util.concurrent.ConcurrentHashMap

class ServiceRegistry {

    private val services = ConcurrentHashMap<Class<*>, Any>()

    fun <T : Any> register(type: Class<T>, instance: T) {
        services[type] = instance
        if (type == HologramService::class.java) {
            AxoHologramProvider.register(instance as HologramService)
        }
    }

    inline fun <reified T : Any> register(instance: T) {
        register(T::class.java, instance)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: Class<T>): T? = services[type] as? T

    inline fun <reified T : Any> get(): T? = get(T::class.java)

    fun clear() {
        AxoHologramProvider.unregister()
        services.clear()
    }
}
