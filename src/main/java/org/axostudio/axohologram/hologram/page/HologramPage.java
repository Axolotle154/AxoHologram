package org.axostudio.axohologram.hologram.page;

import org.axostudio.axohologram.hologram.line.HologramLine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public interface HologramPage {

    List<HologramLine> getLines();
    HologramLine getLine(int index);
    void addLine(HologramLine line);
    void insertLine(int index, HologramLine line);
    void setLine(int index, HologramLine line);
    void removeLine(int index);

    String getPermission();
    void setPermission(String permission);

    boolean canView(Player player);

    void serialize(ConfigurationSection section);
}
