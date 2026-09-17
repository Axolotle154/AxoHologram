package org.axostudio.axohologram.api.animation;

import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface DisplayAnimation {
    String getName();
    int getTickRate();
    boolean isEnabled();

    record Frame(
            Location location,
            Vector3f scale,
            Quaternionf rotation,
            int interpolationDuration
    ) {}

    Frame calculateFrame(Location baseLocation, long tick);
}
