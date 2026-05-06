package org.axostudio.axohologram.hologram.billboard;

import org.bukkit.entity.Display;

public enum Billboard {
    FIXED,
    CENTER,
    VERTICAL,
    HORIZONTAL;

    public static Billboard fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return CENTER;
        }
    }

    public Display.Billboard toPaperBillboard() {
        return switch (this) {
            case FIXED -> Display.Billboard.FIXED;
            case CENTER -> Display.Billboard.CENTER;
            case VERTICAL -> Display.Billboard.VERTICAL;
            case HORIZONTAL -> Display.Billboard.HORIZONTAL;
        };
    }
}
