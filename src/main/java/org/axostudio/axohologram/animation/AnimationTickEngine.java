package org.axostudio.axohologram.animation;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.HologramManager;
import org.axostudio.axohologram.hologram.impl.AxoHologramImpl;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.bukkit.Bukkit;

import java.util.Collection;

public final class AnimationTickEngine {

    private static final long TPS_SAMPLE_INTERVAL_TICKS = 20L;

    private final AxoHologram plugin;
    private final AnimationManager animationManager;
    private volatile long currentTick;
    private volatile long nextTpsSampleTick;
    private volatile boolean lowTps;
    private volatile boolean skipAnimationsOnLowTps;
    private volatile boolean skipRefreshWhenNoViewers;
    private volatile double lowTpsThreshold;
    private volatile long tickRate;
    private SchedulerUtil.TaskHandle task;

    public AnimationTickEngine(AxoHologram plugin, AnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
    }

    public void start() {
        stop();
        currentTick = 0L;
        nextTpsSampleTick = 0L;
        lowTps = false;
        refreshTaskState();
    }

    public void refreshTaskState() {
        AnimationSettings settings = animationManager.getConfigManager().getSettings();
        if (!settings.enabled() || !hasAnimatedHolograms()) {
            stop();
            return;
        }

        tickRate = Math.max(1L, plugin.getConfigManager().getConfig().getLong("performance.animation-refresh-interval-ticks", settings.tickRate()));
        skipAnimationsOnLowTps = plugin.getConfigManager().getConfig().getBoolean("performance.low-tps-animation-skip", settings.reduceQualityOnLowTps());
        skipRefreshWhenNoViewers = plugin.getConfigManager().getDynamicRefreshRuntimeConfig().skipRefreshWhenNoViewers();
        lowTpsThreshold = plugin.getConfigManager().getConfig().getDouble("performance.low-tps-threshold", 17.0D);
        if (task == null) {
            task = plugin.getSchedulerUtil().runGlobalAtFixedRate(this::tick, tickRate, tickRate);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public long currentTick() {
        return currentTick;
    }

    private void tick() {
        HologramManager hologramManager = plugin.getHologramManager();
        if (hologramManager == null) {
            return;
        }

        Collection<Hologram> animatedHolograms = hologramManager.getAnimatedHolograms();
        if (animatedHolograms.isEmpty()) {
            refreshTaskState();
            return;
        }

        long tick = currentTick + tickRate;
        currentTick = tick;
        if (shouldSkipForLowTps(tick)) {
            return;
        }

        for (Hologram hologram : animatedHolograms) {
            if (hologram.isEnabled()) {
                refreshAnimatedHologram(hologram);
            }
        }
    }

    private boolean hasAnimatedHolograms() {
        HologramManager hologramManager = plugin.getHologramManager();
        return hologramManager != null && hologramManager.hasAnimatedHolograms();
    }

    private void refreshAnimatedHologram(Hologram hologram) {
        if (skipRefreshWhenNoViewers && hologram instanceof AxoHologramImpl axoHologram && !axoHologram.hasActiveViewers()) {
            return;
        }

        if (animationManager.requiresFullAnimationRefresh(hologram)) {
            hologram.refreshViewers();
            return;
        }

        if (hologram instanceof AxoHologramImpl axoHologram) {
            axoHologram.refreshDisplayAnimationViewers();
            return;
        }

        hologram.refreshViewers();
    }

    private boolean shouldSkipForLowTps(long tick) {
        if (!skipAnimationsOnLowTps) {
            return false;
        }

        if (tick >= nextTpsSampleTick) {
            double[] tps = Bukkit.getTPS();
            lowTps = tps.length > 0 && tps[0] < lowTpsThreshold;
            nextTpsSampleTick = tick + TPS_SAMPLE_INTERVAL_TICKS;
        }

        return lowTps;
    }
}
