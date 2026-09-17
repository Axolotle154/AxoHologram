package org.axostudio.axohologram.infrastructure.task

fun interface TaskHandle {
    fun cancel()
    fun isCancelled(): Boolean = false

    companion object {
        @JvmField
        val NOOP: TaskHandle = TaskHandle {}
    }
}
