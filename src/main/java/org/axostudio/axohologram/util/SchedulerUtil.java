package org.axostudio.axohologram.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public final class SchedulerUtil {

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }

    private final Plugin plugin;
    private final boolean isFolia;

    public SchedulerUtil(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = isFolia();
    }

    public void run(Runnable runnable) {
        if (isFolia) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, runnable);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void runLater(Runnable runnable, long delayTicks) {
        if (isFolia) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public BukkitTask runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia) {
            plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), delayTicks, periodTicks);
            return null; // Folia's ScheduledTask is not a BukkitTask
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        }
    }

    public void runGlobal(Runnable runnable) {
        if (isFolia) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, runnable);
        } else {
            run(runnable);
        }
    }

    public TaskHandle runGlobalDelayed(Consumer<ScheduledTask> task, long delayTicks) {
        if (isFolia) {
            ScheduledTask scheduledTask = plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task, delayTicks);
            return scheduledTask::cancel;
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, () -> task.accept(null), delayTicks);
        return bukkitTask::cancel;
    }

    public TaskHandle runGlobalAtFixedRate(Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
        if (isFolia) {
            ScheduledTask scheduledTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task, initialDelayTicks, periodTicks);
            return scheduledTask::cancel;
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> task.accept(null), initialDelayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    public void runAtLocation(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        if (isFolia) {
            if (Bukkit.isOwnedByCurrentRegion(location)) {
                runnable.run();
                return;
            }
            plugin.getServer().getRegionScheduler().execute(plugin, location, runnable);
        } else {
            run(runnable);
        }
    }

    public boolean runAtEntity(Entity entity, Runnable runnable) {
        if (entity == null || !entity.isValid()) {
            return false;
        }

        if (isFolia) {
            if (Bukkit.isOwnedByCurrentRegion(entity)) {
                runnable.run();
                return true;
            }
            return entity.getScheduler().run(plugin, task -> runnable.run(), null) != null;
        } else {
            run(runnable);
            return true;
        }
    }

    public boolean runAtEntityDelayed(Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null || !entity.isValid()) {
            return false;
        }

        if (isFolia) {
            return entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, delayTicks) != null;
        } else {
            runLater(runnable, delayTicks);
            return true;
        }
    }

    public boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
