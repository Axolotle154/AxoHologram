package org.axostudio.axohologram.api.action;

import java.util.Locale;

public enum HologramClickType {
    LEFT,
    RIGHT,
    ANY,
    HOVER,
    OFF_HOVER;

    public static HologramClickType fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "left":
            case "left_click":
            case "leftclick":
                return LEFT;
            case "right":
            case "right_click":
            case "rightclick":
                return RIGHT;
            case "any":
            case "any_click":
            case "anyclick":
            case "both":
            case "all":
                return ANY;
            case "hover":
            case "on_hover":
            case "onhover":
                return HOVER;
            case "off_hover":
            case "off-hover":
            case "offhover":
                return OFF_HOVER;
            default:
                return null;
        }
    }

    public String getDisplayName() {
        switch (this) {
            case ANY:
                return "any_click";
            case OFF_HOVER:
                return "off_hover";
            default:
                return name().toLowerCase(Locale.ROOT);
        }
    }

    public boolean matches(HologramClickType clickType) {
        return this == ANY || this == clickType;
    }
}
