package org.axostudio.axohologram.api.hologram;

import org.axostudio.axohologram.api.action.HologramAction;
import org.axostudio.axohologram.api.action.HologramClickType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface Hologram {

    String getId();
    String getWorldName();
    boolean isPersistent();
    void setPersistent(boolean persistent);
    boolean isEnabled();
    void setEnabled(boolean enabled);

    Location getLocation();
    void setLocation(Location location);
    void setLocation(Location location, boolean persist);
    Vector getOffset();
    void setOffset(Vector offset);

    List<HologramPage> getPages();
    HologramPage getPage(int index);
    void addPage(HologramPage page);
    void removePage(int index);
    int pageCount();

    void addLine(String line);
    void addLine(HologramLine line);
    void addLines(List<String> lines);
    void addLines(Collection<? extends HologramLine> lines);
    void addTextLine(String line);
    void addTextLines(Collection<String> lines);

    default void addLines(String... lines) {
        addTextLines(Arrays.asList(lines));
    }

    default void addTextLines(String... lines) {
        addTextLines(Arrays.asList(lines));
    }

    default void addLines(HologramLine... lines) {
        addLines(Arrays.asList(lines));
    }

    default int getDefaultPageIndex() {
        return 0;
    }
    default void setDefaultPageIndex(int index) {}

    String getGroup();
    void setGroup(String group);
    String getPermission();
    void setPermission(String permission);
    org.axostudio.axohologram.core.hologram.visibility.VisibilityMode getVisibilityMode();
    void setVisibilityMode(org.axostudio.axohologram.core.hologram.visibility.VisibilityMode visibilityMode);
    int getViewDistance();
    void setViewDistance(int viewDistance);
    float getScale();
    void setScale(float scale);
    double getHeight();
    default void setHeight(double height) {}
    default void clearHeight() {}
    default boolean hasHeightOverride() {
        return false;
    }
    default double getHeight(int pageIndex) {
        return getHeight();
    }
    default double getLineHeight(HologramLine line) {
        return getHeight();
    }

    default float getScaleX() {
        return getScale();
    }

    default float getScaleY() {
        return getScale();
    }

    default float getScaleZ() {
        return getScale();
    }

    default void setScale(float scaleX, float scaleY, float scaleZ) {
        setScale(Math.max(scaleX, Math.max(scaleY, scaleZ)));
    }

    org.bukkit.entity.Display.Billboard getBillboard();
    void setBillboard(org.bukkit.entity.Display.Billboard billboard);
    float getShadowStrength();
    void setShadowStrength(float shadowStrength);
    float getShadowRadius();
    void setShadowRadius(float shadowRadius);
    int getBrightnessBlock();
    void setBrightnessBlock(int blockBrightness);
    int getBrightnessSky();
    void setBrightnessSky(int skyBrightness);
    Color getBackgroundColor();
    void setBackgroundColor(Color backgroundColor);
    boolean hasTextShadow();
    void setTextShadow(boolean textShadow);
    boolean isSeeThrough();
    void setSeeThrough(boolean seeThrough);
    TextDisplay.TextAlignment getAlignment();
    void setAlignment(TextDisplay.TextAlignment alignment);
    long getUpdateTextInterval();
    void setUpdateTextInterval(long updateTextInterval);
    String getDisplayAnimation();
    void setDisplayAnimation(String displayAnimation);
    boolean isDisplayAnimationEnabled();
    void setDisplayAnimationEnabled(boolean enabled);
    String getEffectivePermission();
    String getLinkedNpc();
    void setLinkedNpc(String linkedNpc);
    List<HologramAction> getActions(HologramClickType clickType);
    void addAction(HologramClickType clickType, HologramAction action);
    HologramAction removeAction(HologramClickType clickType, int index);
    void executeActions(Player player, HologramClickType clickType);
    default boolean setCurrentPage(Player player, int pageIndex) {
        return false;
    }
    default int getCurrentPage(Player player) {
        return 0;
    }
    default boolean changePage(Player player, int delta) {
        return false;
    }

    default void show(Player player) {}
    default void hide(Player player) {}
    default void update(Player player) {}
    default void destroy() {}
    default void refreshViewers() {}
    default boolean requiresPeriodicRefresh() {
        return false;
    }

    default void updateVisibility(Player player, boolean force) {}

    default void updateVisibility(Player player) {
        updateVisibility(player, false);
    }

    default boolean isViewing(Player player) {
        return false;
    }
    default boolean canView(Player player) {
        return true;
    }

    default void serialize(ConfigurationSection section) {}

    default Hologram clone() {
        return this;
    }
}
