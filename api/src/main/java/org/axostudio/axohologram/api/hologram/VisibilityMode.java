package org.axostudio.axohologram.api.hologram;

import java.util.Locale;

public enum VisibilityMode {
    ALL,
    PERMISSION,
    CONDITION,
    MANUAL,
    SCRIPT,
    RADIUS,
    NONE;

    public static VisibilityMode fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ALL;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
