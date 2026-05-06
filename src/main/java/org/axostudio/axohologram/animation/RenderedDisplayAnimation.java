package org.axostudio.axohologram.animation;

import org.bukkit.Location;

public record RenderedDisplayAnimation(
        Location location,
        float scaleMultiplier,
        float rollOffset,
        int interpolationDuration
) {
}
