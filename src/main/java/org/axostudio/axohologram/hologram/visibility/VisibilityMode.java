package org.axostudio.axohologram.hologram.visibility;

public enum VisibilityMode {
    ALL,
    MANUAL,
    PERMISSION;

    public static VisibilityMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }

        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }
}
