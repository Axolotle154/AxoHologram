package org.axostudio.axohologram.infrastructure.scheduler

import org.axostudio.axohologram.platform.scheduler.AxoScheduler
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

class TaskScheduler(private val plugin: Plugin) {

    private val backend: AxoScheduler = AxoScheduler.create(plugin)

    val isFolia: Boolean
        get() = backend.isFolia

    fun run(action: Runnable): AxoScheduler.TaskHandle = backend.run(action)

    fun runLater(delayTicks: Long, action: Runnable): AxoScheduler.TaskHandle =
        backend.runLater(action, delayTicks)

    fun runTimer(delayTicks: Long, periodTicks: Long, action: Runnable): AxoScheduler.TaskHandle =
        backend.runTimer(action, delayTicks, periodTicks)

    fun runGlobal(action: Runnable): AxoScheduler.TaskHandle = backend.runGlobal(action)

    fun runGlobalDelayed(delayTicks: Long, action: Runnable): AxoScheduler.TaskHandle =
        backend.runGlobalDelayed(action, delayTicks)

    fun runGlobalTimer(initialDelayTicks: Long, periodTicks: Long, action: Runnable): AxoScheduler.TaskHandle =
        backend.runGlobalTimer(action, initialDelayTicks, periodTicks)

    fun runAtEntity(entity: Entity, action: Runnable): AxoScheduler.TaskHandle =
        backend.runAtEntity(entity, action)

    fun runAtEntityDelayed(entity: Entity, delayTicks: Long, action: Runnable): AxoScheduler.TaskHandle =
        backend.runAtEntityDelayed(entity, action, delayTicks)

    fun runAtLocation(location: Location, action: Runnable): AxoScheduler.TaskHandle =
        backend.runAtLocation(location, action)

    fun runAtLocationDelayed(location: Location, delayTicks: Long, action: Runnable): AxoScheduler.TaskHandle =
        backend.runAtLocationDelayed(location, action, delayTicks)

    fun runAsync(action: Runnable): AxoScheduler.TaskHandle = backend.runAsync(action)

    fun runAsyncDelayed(delayTicks: Long, action: Runnable): AxoScheduler.TaskHandle =
        backend.runAsyncDelayed(action, delayTicks)
}
