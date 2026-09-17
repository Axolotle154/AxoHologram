package org.axostudio.axohologram.api.hologram;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public interface HologramLine {

    org.axostudio.axohologram.core.hologram.line.LineType getType();

    String getContent();
    void setContent(String content);

    Vector getOffset();
    void setOffset(Vector offset);

    double getHeight();
    void setHeight(double height);
    void clearHeight();
    boolean hasHeightOverride();

    org.bukkit.entity.Display.Billboard getBillboard();
    void setBillboard(org.bukkit.entity.Display.Billboard billboard);
    boolean hasBillboardOverride();

    String getPermission();
    void setPermission(String permission);

    boolean canView(Player player);

    default boolean hasDisplayAnimationOverride() {
        return false;
    }

    default String getDisplayAnimationOverride() {
        return null;
    }

    default void setDisplayAnimationOverride(String animationName) {
    }

    default float getScaleX() {
        return 1.0f;
    }

    default float getScaleY() {
        return 1.0f;
    }

    default float getScaleZ() {
        return 1.0f;
    }

    default void setScale(float scale) {
        setScale(scale, scale, scale);
    }

    default void setScale(float scaleX, float scaleY, float scaleZ) {
    }

    default void serialize(ConfigurationSection section) {}
}
