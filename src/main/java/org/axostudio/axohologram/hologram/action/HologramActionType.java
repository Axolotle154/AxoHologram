package org.axostudio.axohologram.hologram.action;

import java.util.Locale;

public enum HologramActionType {
    COMMAND,
    CONSOLE_COMMAND,
    MESSAGE,
    PAGE,
    SOUND;

    public static HologramActionType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "command" -> COMMAND;
            case "console_command", "console-command", "consolecommand" -> CONSOLE_COMMAND;
            case "message", "msg" -> MESSAGE;
            case "page" -> PAGE;
            case "sound" -> SOUND;
            default -> null;
        };
    }

    public String getDisplayName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
