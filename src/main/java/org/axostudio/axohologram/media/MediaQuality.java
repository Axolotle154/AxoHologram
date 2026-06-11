package org.axostudio.axohologram.media;

public enum MediaQuality {
    LOW,
    MEDIUM,
    HIGH;

    public static MediaQuality fromString(String raw, MediaQuality fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        for (MediaQuality value : values()) {
            if (value.name().equalsIgnoreCase(raw.trim())) {
                return value;
            }
        }
        return fallback;
    }
}
