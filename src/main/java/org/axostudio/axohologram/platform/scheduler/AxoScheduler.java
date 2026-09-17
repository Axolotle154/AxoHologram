package org.axostudio.axohologram.platform.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public interface AxoScheduler {

    @FunctionalInterface
    interface TaskHandle {
        void cancel();
        default boolean isCancelled() {
            return false;
        }
    }

    TaskHandle NOOP_HANDLE = () -> {};

    boolean isFolia();

    TaskHandle run(Runnable runnable);
    TaskHandle runLater(Runnable runnable, long delayTicks);
    TaskHandle runTimer(Runnable runnable, long delayTicks, long periodTicks);

    TaskHandle runGlobal(Runnable runnable);
    TaskHandle runGlobalDelayed(Runnable runnable, long delayTicks);
    TaskHandle runGlobalTimer(Runnable runnable, long initialDelayTicks, long periodTicks);

    TaskHandle runAtEntity(Entity entity, Runnable runnable);
    TaskHandle runAtEntityDelayed(Entity entity, Runnable runnable, long delayTicks);

    TaskHandle runAtLocation(Location location, Runnable runnable);
    TaskHandle runAtLocationDelayed(Location location, Runnable runnable, long delayTicks);

    TaskHandle runAsync(Runnable runnable);
    TaskHandle runAsyncDelayed(Runnable runnable, long delayTicks);

    static AxoScheduler create(Plugin plugin) {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        return folia ? new FoliaScheduler(plugin) : new Paper(plugin);
    }

    class Paper implements AxoScheduler {
        private final Plugin plugin;

        public Paper(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean isFolia() {
            return false;
        }

        private TaskHandle wrap(BukkitTask task) {
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
            if (!plugin.isEnabled()) {
                runnable.run();
                return NOOP_HANDLE;
            }
            return wrap(Bukkit.getScheduler().runTask(plugin, runnable));
        }

        @Override
        public TaskHandle runLater(Runnable runnable, long delayTicks) {
            if (!plugin.isEnabled()) return NOOP_HANDLE;
            return wrap(Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1, delayTicks)));
        }

        @Override
        public TaskHandle runTimer(Runnable runnable, long delayTicks, long periodTicks) {
            if (!plugin.isEnabled()) return NOOP_HANDLE;
            return wrap(Bukkit.getScheduler().runTaskTimer(plugin, runnable, Math.max(1, delayTicks), Math.max(1, periodTicks)));
        }

        @Override
        public TaskHandle runGlobal(Runnable runnable) {
            return run(runnable);
        }

        @Override
        public TaskHandle runGlobalDelayed(Runnable runnable, long delayTicks) {
            return runLater(runnable, delayTicks);
        }

        @Override
        public TaskHandle runGlobalTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
            return runTimer(runnable, initialDelayTicks, periodTicks);
        }

        @Override
        public TaskHandle runAtEntity(Entity entity, Runnable runnable) {
            return run(runnable);
        }

        @Override
        public TaskHandle runAtEntityDelayed(Entity entity, Runnable runnable, long delayTicks) {
            return runLater(runnable, delayTicks);
        }

        @Override
        public TaskHandle runAtLocation(Location location, Runnable runnable) {
            return run(runnable);
        }

        @Override
        public TaskHandle runAtLocationDelayed(Location location, Runnable runnable, long delayTicks) {
            return runLater(runnable, delayTicks);
        }

        @Override
        public TaskHandle runAsync(Runnable runnable) {
            if (!plugin.isEnabled()) {
                runnable.run();
                return NOOP_HANDLE;
            }
            return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
        }

        @Override
        public TaskHandle runAsyncDelayed(Runnable runnable, long delayTicks) {
            if (!plugin.isEnabled()) return NOOP_HANDLE;
            return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, Math.max(1, delayTicks)));
        }
    }
}
