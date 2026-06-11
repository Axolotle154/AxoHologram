package org.axostudio.axohologram.media;

public enum MediaType {
    IMAGE,
    VIDEO;

    public static MediaType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (MediaType value : values()) {
            if (value.name().equalsIgnoreCase(raw.trim())) {
                return value;
            }
        }
        return null;
    }

    public boolean isMedia() {
        return this == IMAGE || this == VIDEO;
    }
}
