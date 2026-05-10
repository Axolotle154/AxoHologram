package org.axostudio.axohologram.hologram.line;

import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public interface HologramLine {

    LineType getType();

    Vector getOffset();
    void setOffset(Vector offset);

    double getHeight();
    void setHeight(double height);
    void clearHeight();
    boolean hasHeightOverride();

    Billboard getBillboard();
    void setBillboard(Billboard billboard);
    boolean hasBillboardOverride();

    String getPermission();
    void setPermission(String permission);

    boolean canView(Player player);

    void spawn(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard);
    void update(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard);
    void destroy(Player player, String hologramId, int pageIndex, int lineIndex);

    void serialize(ConfigurationSection section);
}
