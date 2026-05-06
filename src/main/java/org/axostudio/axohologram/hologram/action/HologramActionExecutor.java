package org.axostudio.axohologram.hologram.action;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.util.MessageUtil;
import org.axostudio.axohologram.util.MiniMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class HologramActionExecutor {

    private HologramActionExecutor() {
    }

    public static HologramAction createValidated(HologramActionType type, String rawValue) {
        if (type == null) {
            throw new IllegalArgumentException("Action type is required.");
        }
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Action value is required.");
        }

        return new HologramAction(type, normalizeValue(type, rawValue));
    }

    public static void execute(AxoHologram plugin, Player player, Hologram hologram, HologramAction action) {
        if (plugin == null || player == null || hologram == null || action == null) {
            return;
        }

        String resolvedValue = replaceTokens(action.getValue(), player, hologram);
        switch (action.getType()) {
            case COMMAND -> plugin.getSchedulerUtil().runAtEntity(player, () ->
                    executeCommandSafely(plugin, player, hologram, action, resolvedValue, false));
            case CONSOLE_COMMAND -> plugin.getSchedulerUtil().runGlobal(() ->
                    executeCommandSafely(plugin, player, hologram, action, resolvedValue, true));
            case MESSAGE -> plugin.getSchedulerUtil().runAtEntity(player, () ->
                    player.sendMessage(MiniMessageUtil.parse(resolvedValue, player)));
            case PAGE -> executePageAction(player, hologram, resolvedValue);
            case SOUND -> executeSoundAction(plugin, player, resolvedValue);
        }
    }

    private static void executeCommandSafely(AxoHologram plugin, Player player, Hologram hologram, HologramAction action, String resolvedValue, boolean console) {
        String command = stripLeadingSlash(resolvedValue);
        try {
            boolean success = console
                    ? Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                    : plugin.getServer().dispatchCommand(player, command);
            if (!success) {
                notifyCommandFailure(plugin, player, hologram, action, command, null);
            }
        } catch (RuntimeException exception) {
            notifyCommandFailure(plugin, player, hologram, action, command, exception);
        }
    }

    private static void notifyCommandFailure(AxoHologram plugin, Player player, Hologram hologram, HologramAction action, String command, RuntimeException exception) {
        plugin.getLogger().warning("Failed to execute " + action.getType().getDisplayName()
                + " action on hologram '" + hologram.getId()
                + "' for player '" + player.getName()
                + "': " + command
                + (exception == null ? "" : " (" + exception.getMessage() + ")"));
        String message = plugin.getConfigManager().getMessages().getString("action-execution-failed");
        if (message != null && !message.isBlank()) {
            MessageUtil.sendMessage(player, message
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<type>", action.getType().getDisplayName())
                    .replace("<value>", command));
        }
    }

    private static String normalizeValue(HologramActionType type, String rawValue) {
        String trimmed = rawValue.trim();
        return switch (type) {
            case COMMAND, CONSOLE_COMMAND, MESSAGE -> trimmed;
            case PAGE -> normalizePageValue(trimmed);
            case SOUND -> normalizeSoundValue(trimmed);
        };
    }

    private static String normalizePageValue(String rawValue) {
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("next")) {
            return "next";
        }
        if (normalized.equals("prev") || normalized.equals("previous") || normalized.equals("back")) {
            return "previous";
        }

        int page = Integer.parseInt(normalized);
        if (page <= 0) {
            throw new IllegalArgumentException("Page action must target a page greater than 0.");
        }
        return String.valueOf(page);
    }

    private static String normalizeSoundValue(String rawValue) {
        String[] parts = rawValue.trim().split("\\s+");
        if (parts.length == 0 || parts.length > 3) {
            throw new IllegalArgumentException("Sound action must use <sound> [volume] [pitch].");
        }

        NamespacedKey soundKey = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
        if (soundKey == null || Registry.SOUNDS.get(soundKey) == null) {
            throw new IllegalArgumentException("Unknown sound: " + parts[0]);
        }

        float volume = parts.length >= 2 ? parseNonNegativeFloat(parts[1], "volume") : 1.0F;
        float pitch = parts.length >= 3 ? parseNonNegativeFloat(parts[2], "pitch") : 1.0F;
        return soundKey.asString() + " " + volume + " " + pitch;
    }

    private static void executePageAction(Player player, Hologram hologram, String value) {
        if (value.equalsIgnoreCase("next")) {
            hologram.changePage(player, 1);
            return;
        }
        if (value.equalsIgnoreCase("previous")) {
            hologram.changePage(player, -1);
            return;
        }

        try {
            hologram.setCurrentPage(player, Integer.parseInt(value) - 1);
        } catch (NumberFormatException ignored) {
        }
    }

    private static void executeSoundAction(AxoHologram plugin, Player player, String value) {
        String[] parts = value.split("\\s+");
        NamespacedKey soundKey = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
        if (soundKey == null) {
            return;
        }
        Sound sound = Registry.SOUNDS.get(soundKey);
        if (sound == null) {
            return;
        }

        float volume = parts.length >= 2 ? parseNonNegativeFloat(parts[1], "volume") : 1.0F;
        float pitch = parts.length >= 3 ? parseNonNegativeFloat(parts[2], "pitch") : 1.0F;
        plugin.getSchedulerUtil().runAtEntity(player, () ->
                player.playSound(player.getLocation(), sound, volume, pitch));
    }

    private static float parseNonNegativeFloat(String rawValue, String label) {
        try {
            float parsed = Float.parseFloat(rawValue);
            if (parsed < 0.0F) {
                throw new IllegalArgumentException("Sound " + label + " must be 0 or greater.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid sound " + label + ": " + rawValue, exception);
        }
    }

    private static String replaceTokens(String value, Player player, Hologram hologram) {
        return value
                .replace("%player%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString())
                .replace("%hologram%", hologram.getId())
                .replace("%hologram_id%", hologram.getId())
                .replace("<player>", player.getName())
                .replace("<player_uuid>", player.getUniqueId().toString())
                .replace("<hologram>", hologram.getId())
                .replace("<hologram_id>", hologram.getId());
    }

    private static String stripLeadingSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
