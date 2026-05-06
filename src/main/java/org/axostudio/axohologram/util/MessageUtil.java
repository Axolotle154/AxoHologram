package org.axostudio.axohologram.util;

import org.axostudio.axohologram.AxoHologram;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageUtil {

    public static void sendMessage(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        String prefix = AxoHologram.getInstance().getConfigManager().getMessages().getString("prefix", "");
        String renderedMessage = message.contains("<prefix>") ? message.replace("<prefix>", prefix) : prefix + message;
        Component parsedMessage = MiniMessageUtil.parse(renderedMessage, sender instanceof Player ? (Player) sender : null);
        sender.sendMessage(parsedMessage);
    }
}
