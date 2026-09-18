package org.axostudio.axohologram.api.action;

import java.util.Locale;

public enum HologramActionType {
    COMMAND,
    CONSOLE_COMMAND,
    MESSAGE,
    PAGE,
    MENU_PAGE,
    SOUND;

    public static HologramActionType fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "command":
                return COMMAND;
            case "console_command":
            case "console-command":
            case "consolecommand":
                return CONSOLE_COMMAND;
            case "message":
            case "msg":
                return MESSAGE;
            case "page":
                return PAGE;
            case "menu_page":
            case "menu-page":
            case "menupage":
                return MENU_PAGE;
            case "sound":
                return SOUND;
            default:
                return null;
        }
    }

    public String getDisplayName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
