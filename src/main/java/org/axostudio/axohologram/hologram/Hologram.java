package org.axostudio.axohologram.hologram;

import org.axostudio.axohologram.hologram.action.HologramAction;
import org.axostudio.axohologram.hologram.action.HologramClickType;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.visibility.VisibilityMode;
import org.bukkit.Location;
import org.bukkit.Color;
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

    int getDefaultPageIndex();
    void setDefaultPageIndex(int index);

    String getPermission();
    void setPermission(String permission);
    VisibilityMode getVisibilityMode();
    void setVisibilityMode(VisibilityMode visibilityMode);
    int getViewDistance();
    void setViewDistance(int viewDistance);
    float getScale();
    void setScale(float scale);
    double getHeight();
    double getHeight(int pageIndex);
    double getLineHeight(HologramLine line);

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

    Billboard getBillboard();
    void setBillboard(Billboard billboard);
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
    boolean setCurrentPage(Player player, int pageIndex);
    boolean changePage(Player player, int delta);

    void show(Player player);
    void hide(Player player);
    void update(Player player);
    void destroy();
    void refreshViewers();
    boolean requiresPeriodicRefresh();

    void updateVisibility(Player player, boolean force);

    default void updateVisibility(Player player) {
        updateVisibility(player, false);
    }

    boolean isViewing(Player player);
    boolean canView(Player player);

    void serialize(ConfigurationSection section);
}
