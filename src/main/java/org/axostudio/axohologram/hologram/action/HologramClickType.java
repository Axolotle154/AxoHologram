package org.axostudio.axohologram.hologram.action;

import java.util.Locale;

public enum HologramClickType {
    LEFT,
    RIGHT,
    ANY;

    public static HologramClickType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "left", "left_click", "leftclick" -> LEFT;
            case "right", "right_click", "rightclick" -> RIGHT;
            case "any", "any_click", "anyclick", "both", "all" -> ANY;
            default -> null;
        };
    }

    public String getDisplayName() {
        return this == ANY ? "any_click" : name().toLowerCase(Locale.ROOT);
    }

    public boolean matches(HologramClickType clickType) {
        return this == ANY || this == clickType;
    }
}
