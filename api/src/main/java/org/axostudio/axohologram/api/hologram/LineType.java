package org.axostudio.axohologram.api.hologram;

import java.util.Locale;

public enum LineType {
    TEXT,
    ITEM,
    BLOCK,
    COMPOSITE,
    ANIMATED_TEXT,
    UNKNOWN;

    public static LineType fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return TEXT;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
