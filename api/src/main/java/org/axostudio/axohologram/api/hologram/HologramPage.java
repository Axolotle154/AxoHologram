package org.axostudio.axohologram.api.hologram;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public interface HologramPage {

    default int getIndex() {
        return 0;
    }

    default void setIndex(int index) {
    }

    List<HologramLine> getLines();
    HologramLine getLine(int index);
    void addLine(HologramLine line);
    default void addLine(String line) {}
    void insertLine(int index, HologramLine line);
    void setLine(int index, HologramLine line);
    default void setLine(int index, String line) {}
    void removeLine(int index);
    int lineCount();

    default void clearLines() {
        getLines().clear();
    }

    default boolean isEmpty() {
        return lineCount() == 0;
    }

    default String getPermission() {
        return null;
    }

    default void setPermission(String permission) {
    }

    default boolean canView(Player player) {
        String perm = getPermission();
        return perm == null || perm.isEmpty() || (player != null && player.hasPermission(perm));
    }

    default void serialize(ConfigurationSection section) {
    }

    default HologramPage clone() {
        return this;
    }
}
