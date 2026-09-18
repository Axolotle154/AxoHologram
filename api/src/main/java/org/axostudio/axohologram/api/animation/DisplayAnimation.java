package org.axostudio.axohologram.api.animation;

import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

public interface DisplayAnimation {
    String getName();
    int getTickRate();
    boolean isEnabled();

    final class Frame {
        private final Location location;
        private final Vector3f scale;
        private final Quaternionf rotation;
        private final int interpolationDuration;

        public Frame(Location location, Vector3f scale, Quaternionf rotation, int interpolationDuration) {
            this.location = location;
            this.scale = scale;
            this.rotation = rotation;
            this.interpolationDuration = interpolationDuration;
        }

        public Location location() {
            return location;
        }

        public Vector3f scale() {
            return scale;
        }

        public Quaternionf rotation() {
            return rotation;
        }

        public int interpolationDuration() {
            return interpolationDuration;
        }

        public Location getLocation() {
            return location;
        }

        public Vector3f getScale() {
            return scale;
        }

        public Quaternionf getRotation() {
            return rotation;
        }

        public int getInterpolationDuration() {
            return interpolationDuration;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Frame frame = (Frame) o;
            return interpolationDuration == frame.interpolationDuration &&
                    Objects.equals(location, frame.location) &&
                    Objects.equals(scale, frame.scale) &&
                    Objects.equals(rotation, frame.rotation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, scale, rotation, interpolationDuration);
        }

        @Override
        public String toString() {
            return "Frame[" +
                    "location=" + location +
                    ", scale=" + scale +
                    ", rotation=" + rotation +
                    ", interpolationDuration=" + interpolationDuration +
                    ']';
        }
    }

    Frame calculateFrame(Location baseLocation, long tick);
}
