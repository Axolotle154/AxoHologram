package org.axostudio.axohologram.api.action;

import java.util.Locale;

public enum HologramClickType {
    LEFT,
    RIGHT,
    ANY,
    HOVER,
    OFF_HOVER;

    public static HologramClickType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "left", "left_click", "leftclick" -> LEFT;
            case "right", "right_click", "rightclick" -> RIGHT;
            case "any", "any_click", "anyclick", "both", "all" -> ANY;
            case "hover", "on_hover", "onhover" -> HOVER;
            case "off_hover", "off-hover", "offhover" -> OFF_HOVER;
            default -> null;
        };
    }

    public String getDisplayName() {
        return switch (this) {
            case ANY -> "any_click";
            case OFF_HOVER -> "off_hover";
            default -> name().toLowerCase(Locale.ROOT);
        };
    }

    public boolean matches(HologramClickType clickType) {
        return this == ANY || this == clickType;
    }
}
