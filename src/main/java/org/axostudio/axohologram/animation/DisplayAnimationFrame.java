package org.axostudio.axohologram.animation;

public record DisplayAnimationFrame(
        double offsetX,
        double offsetY,
        double offsetZ,
        float yawOffset,
        float pitchOffset,
        float rollOffset,
        float scaleMultiplier
) {

    public static final DisplayAnimationFrame IDENTITY =
            new DisplayAnimationFrame(0.0D, 0.0D, 0.0D, 0.0F, 0.0F, 0.0F, 1.0F);
}
