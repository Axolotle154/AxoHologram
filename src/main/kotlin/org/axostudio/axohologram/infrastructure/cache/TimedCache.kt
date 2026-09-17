package org.axostudio.axohologram.infrastructure.cache

import java.util.concurrent.ConcurrentHashMap

class TimedCache<K : Any, V : Any>(
    private val defaultTtlMillis: Long = 1000L,
    private val maxSize: Int = 1024
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val map = ConcurrentHashMap<K, Entry<V>>()

    operator fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V, ttlMillis: Long = defaultTtlMillis) {
        if (map.size >= maxSize) {
            cleanup()
        }
        map[key] = Entry(value, System.currentTimeMillis() + ttlMillis)
    }

    fun getOrPut(key: K, ttlMillis: Long = defaultTtlMillis, defaultValue: () -> V): V {
        val existing = get(key)
        if (existing != null) return existing

        val computed = defaultValue()
        put(key, computed, ttlMillis)
        return computed
    }

    fun remove(key: K): V? = map.remove(key)?.value

    fun clear() {
        map.clear()
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        map.entries.removeIf { it.value.expiresAt <= now }
    }
}
