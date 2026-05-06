package org.axostudio.axohologram.animation;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicLong;

public final class AnimationTickEngine {

    private final AxoHologram plugin;
    private final AnimationManager animationManager;
    private final AtomicLong currentTick = new AtomicLong();
    private SchedulerUtil.TaskHandle task;

    public AnimationTickEngine(AxoHologram plugin, AnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
    }

    public void start() {
        stop();
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

        if (plugin.getHologramManager() == null) {
            return;
        }

        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            if (requiresAnimationTick(hologram)) {
                hologram.refreshViewers();
            }
        }
    }

    private boolean requiresAnimationTick(Hologram hologram) {
        String displayAnimation = animationManager.resolveDisplayAnimationName(hologram);
        if (displayAnimation != null && animationManager.hasDisplayAnimation(displayAnimation)) {
            return true;
        }

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

        double[] tps = Bukkit.getTPS();
        return tps.length > 0 && tps[0] < 17.0D && tick % (tickRate * 2L) != 0L;
    }
}
