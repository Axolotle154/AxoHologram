package org.axostudio.axohologram.animation;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.HologramManager;
import org.axostudio.axohologram.hologram.impl.AxoHologramImpl;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

public final class AnimationTickEngine {

    private static final long TPS_SAMPLE_INTERVAL_TICKS = 20L;
    private static final long ANIMATION_SCAN_INTERVAL_TICKS = 20L;

    private final AxoHologram plugin;
    private final AnimationManager animationManager;
    private volatile long currentTick;
    private volatile List<AnimatedHologram> animatedHolograms = List.of();
    private volatile long nextAnimationScanTick;
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
        animatedHolograms = List.of();
        nextAnimationScanTick = 0L;
        nextTpsSampleTick = 0L;
        lowTps = false;
        AnimationSettings settings = animationManager.getConfigManager().getSettings();
        if (!settings.enabled()) {
            return;
        }

        tickRate = Math.max(1L, plugin.getConfigManager().getConfig().getLong("performance.animation-refresh-interval-ticks", settings.tickRate()));
        skipAnimationsOnLowTps = plugin.getConfigManager().getConfig().getBoolean("performance.low-tps-animation-skip", settings.reduceQualityOnLowTps());
        skipRefreshWhenNoViewers = plugin.getConfigManager().getConfig().getBoolean("performance.skip-refresh-when-no-viewers", true);
        lowTpsThreshold = plugin.getConfigManager().getConfig().getDouble("performance.low-tps-threshold", 17.0D);
        task = plugin.getSchedulerUtil().runGlobalAtFixedRate(this::tick, tickRate, tickRate);
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
        long tick = currentTick + tickRate;
        currentTick = tick;
        if (shouldSkipForLowTps(tick)) {
            return;
        }

        HologramManager hologramManager = plugin.getHologramManager();
        if (hologramManager == null) {
            return;
        }

        if (tick >= nextAnimationScanTick) {
            refreshAnimatedHolograms(hologramManager, tick);
        }

        for (AnimatedHologram animatedHologram : animatedHolograms) {
            Hologram hologram = animatedHologram.hologram();
            if (hologram.isEnabled()) {
                refreshAnimatedHologram(animatedHologram);
            }
        }
    }

    private void refreshAnimatedHologram(AnimatedHologram animatedHologram) {
        Hologram hologram = animatedHologram.hologram();
        if (skipRefreshWhenNoViewers && hologram instanceof AxoHologramImpl axoHologram && !axoHologram.hasActiveViewers()) {
            return;
        }

        if (animatedHologram.requiresFullRefresh()) {
            hologram.refreshViewers();
            return;
        }

        if (hologram instanceof AxoHologramImpl axoHologram) {
            axoHologram.refreshDisplayAnimationViewers();
            return;
        }

        hologram.refreshViewers();
    }

    private void refreshAnimatedHolograms(HologramManager hologramManager, long tick) {
        List<AnimatedHologram> refreshedHolograms = new ArrayList<>();
        for (Hologram hologram : hologramManager.getAllHolograms()) {
            AnimationMode animationMode = resolveAnimationMode(hologram);
            if (animationMode != AnimationMode.NONE) {
                refreshedHolograms.add(new AnimatedHologram(hologram, animationMode == AnimationMode.FULL_REFRESH));
            }
        }

        animatedHolograms = refreshedHolograms.isEmpty() ? List.of() : List.copyOf(refreshedHolograms);
        nextAnimationScanTick = tick + ANIMATION_SCAN_INTERVAL_TICKS;
    }

    private AnimationMode resolveAnimationMode(Hologram hologram) {
        if (!hologram.isEnabled()) {
            return AnimationMode.NONE;
        }

        if (hasTextAnimation(hologram)) {
            return AnimationMode.FULL_REFRESH;
        }

        return hasDisplayAnimation(hologram) ? AnimationMode.DISPLAY_STATE : AnimationMode.NONE;
    }

    private boolean hasDisplayAnimation(Hologram hologram) {
        String displayAnimation = animationManager.resolveDisplayAnimationName(hologram);
        return displayAnimation != null && animationManager.hasDisplayAnimation(displayAnimation);
    }

    private boolean hasTextAnimation(Hologram hologram) {
        for (HologramPage page : hologram.getPages()) {
            for (HologramLine line : page.getLines()) {
                if (line instanceof TextLineImpl textLine && animationManager.hasTextAnimationTags(textLine.getContent())) {
                    return true;
                }
            }
        }
        return false;
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

    private record AnimatedHologram(Hologram hologram, boolean requiresFullRefresh) {
    }

    private enum AnimationMode {
        NONE,
        DISPLAY_STATE,
        FULL_REFRESH
    }
}
