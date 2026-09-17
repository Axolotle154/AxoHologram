package org.axostudio.axohologram.platform.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class FoliaScheduler implements AxoScheduler {

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    private TaskHandle wrap(ScheduledTask task) {
        if (task == null) return NOOP_HANDLE;
        return new TaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    @Override
    public TaskHandle run(Runnable runnable) {
        return runGlobal(runnable);
    }

    @Override
    public TaskHandle runLater(Runnable runnable, long delayTicks) {
        return runGlobalDelayed(runnable, delayTicks);
    }

    @Override
    public TaskHandle runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return runGlobalTimer(runnable, delayTicks, periodTicks);
    }

    @Override
    public TaskHandle runGlobal(Runnable runnable) {
        if (!plugin.isEnabled()) {
            runnable.run();
            return NOOP_HANDLE;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, runnable);
        return NOOP_HANDLE;
    }

    @Override
    public TaskHandle runGlobalDelayed(Runnable runnable, long delayTicks) {
        if (!plugin.isEnabled()) return NOOP_HANDLE;
        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runDelayed(
                plugin,
                t -> runnable.run(),
                Math.max(1L, delayTicks)
        );
        return wrap(task);
    }

    @Override
    public TaskHandle runGlobalTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        if (!plugin.isEnabled()) return NOOP_HANDLE;
        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                t -> runnable.run(),
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks)
        );
        return wrap(task);
    }

    @Override
    public TaskHandle runAtEntity(Entity entity, Runnable runnable) {
        if (entity == null || !entity.isValid() || !plugin.isEnabled()) return NOOP_HANDLE;
        ScheduledTask task = entity.getScheduler().run(
                plugin,
                t -> runnable.run(),
                null
        );
        return wrap(task);
    }

    @Override
    public TaskHandle runAtEntityDelayed(Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null || !entity.isValid() || !plugin.isEnabled()) return NOOP_HANDLE;
        ScheduledTask task = entity.getScheduler().runDelayed(
                plugin,
                t -> runnable.run(),
                null,
                Math.max(1L, delayTicks)
        );
        return wrap(task);
    }

    @Override
    public TaskHandle runAtLocation(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null || !plugin.isEnabled()) return NOOP_HANDLE;
        ScheduledTask task = plugin.getServer().getRegionScheduler().run(
                plugin,
                location,
                t -> runnable.run()
        );
        return wrap(task);
    }

    @Override
    public TaskHandle runAtLocationDelayed(Location location, Runnable runnable, long delayTicks) {
        if (location == null || location.getWorld() == null || !plugin.isEnabled()) return NOOP_HANDLE;
        ScheduledTask task = plugin.getServer().getRegionScheduler().runDelayed(
                plugin,
                location,
                t -> runnable.run(),
                Math.max(1L, delayTicks)
        );
        return wrap(task);
    }

    @Override
    public TaskHandle runAsync(Runnable runnable) {
        if (!plugin.isEnabled()) return NOOP_HANDLE;
        ScheduledTask task = plugin.getServer().getAsyncScheduler().runNow(
                plugin,
                t -> runnable.run()
        );
        return wrap(task);
    }

    @Override
    public TaskHandle runAsyncDelayed(Runnable runnable, long delayTicks) {
        if (!plugin.isEnabled()) return NOOP_HANDLE;
        long millis = Math.max(1L, delayTicks * 50L);
        ScheduledTask task = plugin.getServer().getAsyncScheduler().runDelayed(
                plugin,
                t -> runnable.run(),
                millis,
                TimeUnit.MILLISECONDS
        );
        return wrap(task);
    }
}
