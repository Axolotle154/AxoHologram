package org.axostudio.axohologram.animation;

public record AnimationSettings(
        boolean enabled,
        long tickRate,
        boolean cacheFrames,
        boolean asyncTextProcessing,
        boolean allowPlaceholdersInsideAnimations,
        boolean reduceQualityOnLowTps,
        boolean displayInterpolation
) {

    public AnimationSettings {
        tickRate = Math.max(1L, tickRate);
    }

    public static AnimationSettings defaults() {
        return new AnimationSettings(true, 2L, true, true, false, true, true);
    }

    public int interpolationDuration() {
        return displayInterpolation ? (int) Math.min(Math.max(tickRate, 1L), 20L) : 0;
    }
}
