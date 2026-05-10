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
import java.util.concurrent.atomic.AtomicLong;

public final class AnimationTickEngine {

    private static final long TPS_SAMPLE_INTERVAL_TICKS = 20L;
    private static final long ANIMATION_SCAN_INTERVAL_TICKS = 20L;

    private final AxoHologram plugin;
    private final AnimationManager animationManager;
    private final AtomicLong currentTick = new AtomicLong();
    private volatile List<AnimatedHologram> animatedHolograms = List.of();
    private volatile long nextAnimationScanTick;
    private volatile long nextTpsSampleTick;
    private volatile boolean lowTps;
    private SchedulerUtil.TaskHandle task;

    public AnimationTickEngine(AxoHologram plugin, AnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
    }

    public void start() {
        stop();
        animatedHolograms = List.of();
        nextAnimationScanTick = 0L;
        nextTpsSampleTick = 0L;
        lowTps = false;
        AnimationSettings settings = animationManager.getConfigManager().getSettings();
        if (!settings.enabled()) {
            return;
        }

        long tickRate = settings.tickRate();
        task = plugin.getSchedulerUtil().runGlobalAtFixedRate(ignored -> tick(tickRate), tickRate, tickRate);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public long currentTick() {
        return currentTick.get();
    }

    private void tick(long tickRate) {
        long tick = currentTick.addAndGet(tickRate);
        if (shouldSkipForLowTps(tick, tickRate)) {
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
            if (hologram.isEnabled() && hologramManager.getHologram(hologram.getId()) == hologram) {
                refreshAnimatedHologram(animatedHologram);
            }
        }
    }

    private void refreshAnimatedHologram(AnimatedHologram animatedHologram) {
        Hologram hologram = animatedHologram.hologram();
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

    private boolean shouldSkipForLowTps(long tick, long tickRate) {
        if (!animationManager.getConfigManager().getSettings().reduceQualityOnLowTps()) {
            return false;
        }

        if (tick >= nextTpsSampleTick) {
            double[] tps = Bukkit.getTPS();
            lowTps = tps.length > 0 && tps[0] < 17.0D;
            nextTpsSampleTick = tick + TPS_SAMPLE_INTERVAL_TICKS;
        }
        return lowTps && tick % (tickRate * 2L) != 0L;
    }

    private record AnimatedHologram(Hologram hologram, boolean requiresFullRefresh) {
    }

    private enum AnimationMode {
        NONE,
        DISPLAY_STATE,
        FULL_REFRESH
    }
}
