package org.axostudio.axohologram.api.animation;

import org.bukkit.entity.Player;

public interface TextAnimation {
    String getName();
    int getSpeed();
    String animate(String text, Player viewer, long tick);
}
