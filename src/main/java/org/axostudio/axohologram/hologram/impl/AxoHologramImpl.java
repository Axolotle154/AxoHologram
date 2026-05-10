package org.axostudio.axohologram.hologram.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.action.HologramAction;
import org.axostudio.axohologram.hologram.action.HologramActionExecutor;
import org.axostudio.axohologram.hologram.action.HologramClickType;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.impl.BlockLineImpl;
import org.axostudio.axohologram.hologram.line.impl.ItemLineImpl;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.hologram.page.impl.AxoHologramPageImpl;
import org.axostudio.axohologram.hologram.visibility.VisibilityMode;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.axostudio.axohologram.util.ColorUtil;
import org.axostudio.axohologram.util.MiniMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class AxoHologramImpl implements Hologram {

    private final AxoHologram plugin;
    private final String id;
    private final List<HologramPage> pages = new CopyOnWriteArrayList<>();
    private final Map<UUID, PlayerHologramData> viewers = new ConcurrentHashMap<>();
    private final Map<HologramClickType, List<HologramAction>> actions = new ConcurrentHashMap<>();
    private final Map<String, Component> staticLinesCache = new ConcurrentHashMap<>();

    private volatile boolean persistent;
    private volatile boolean enabled;
    private volatile Location location;
    private volatile String worldName;
    private volatile Vector offset;
    private volatile String permission;
    private volatile VisibilityMode visibilityMode;
    private volatile int viewDistance;
    private volatile float scale;
    private volatile float scaleY;
    private volatile float scaleZ;
    private volatile Billboard billboard;
    private volatile float shadowStrength;
    private volatile float shadowRadius;
    private volatile int brightnessBlock;
    private volatile int brightnessSky;
    private volatile Color backgroundColor;
    private volatile boolean textShadow;
    private volatile boolean seeThrough;
    private volatile TextDisplay.TextAlignment alignment;
    private volatile long updateTextInterval;
    private volatile String displayAnimation;
    private volatile boolean displayAnimationEnabled;
    private volatile int defaultPageIndex;
    private volatile String linkedNpc;
    private volatile long lastPeriodicRefreshTick;

    public AxoHologramImpl(String id, Location location, AxoHologram plugin) {
        this.id = id;
        this.location = location.clone();
        this.plugin = plugin;
        this.persistent = true;
        this.enabled = true;
        this.worldName = location.getWorld() != null ? location.getWorld().getName() : null;
        this.offset = new Vector();
        this.permission = null;
        this.visibilityMode = VisibilityMode.ALL;
        this.viewDistance = -1;
        this.scale = 1.0F;
        this.scaleY = 1.0F;
        this.scaleZ = 1.0F;
        this.billboard = Billboard.fromString(plugin.getConfigManager().getConfig().getString("general.defaults.billboard", "center"));
        this.shadowStrength = 1.0F;
        this.shadowRadius = 0.0F;
        this.brightnessBlock = -1;
        this.brightnessSky = -1;
        this.backgroundColor = null;
        this.textShadow = false;
        this.seeThrough = false;
        this.alignment = TextDisplay.TextAlignment.CENTER;
        this.updateTextInterval = -1L;
        this.displayAnimation = readDefaultDisplayAnimationName(plugin);
        this.displayAnimationEnabled = readDefaultDisplayAnimationEnabled(plugin);
        this.defaultPageIndex = 0;
        this.lastPeriodicRefreshTick = Long.MIN_VALUE;
        this.pages.add(new AxoHologramPageImpl());
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getWorldName() {
        return worldName;
    }

    @Override
    public boolean isPersistent() {
        return persistent;
    }

    @Override
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getHologramManager().saveHologram(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateVisibility(player, true);
        }
    }

    @Override
    public Location getLocation() {
        resolveWorldIfNeeded();
        return location.clone();
    }

    @Override
    public void setLocation(Location location) {
        setLocation(location, true);
    }

    @Override
    public void setLocation(Location location, boolean persist) {
        Location targetLocation = Objects.requireNonNull(location, "location").clone();
        this.location = targetLocation;
        this.worldName = targetLocation.getWorld() != null ? targetLocation.getWorld().getName() : this.worldName;
        if (persist) {
            plugin.getHologramManager().saveHologram(this);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateVisibility(player, true);
        }
    }

    @Override
    public Vector getOffset() {
        return offset.clone();
    }

    @Override
    public void setOffset(Vector offset) {
        this.offset = offset == null ? new Vector() : offset.clone();
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public List<HologramPage> getPages() {
        return Collections.unmodifiableList(pages);
    }

    @Override
    public HologramPage getPage(int index) {
        return index >= 0 && index < pages.size() ? pages.get(index) : null;
    }

    @Override
    public void addPage(HologramPage page) {
        if (page == null) {
            return;
        }

        pages.add(page);
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public void removePage(int index) {
        if (index < 0 || index >= pages.size() || pages.size() <= 1) {
            return;
        }

        pages.remove(index);
        defaultPageIndex = clampPageIndex(defaultPageIndex);
        for (PlayerHologramData data : viewers.values()) {
            data.setCurrentPageIndex(clampPageIndex(data.getCurrentPageIndex()));
        }

        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public void addLine(String line) {
        addTextLine(line);
    }

    @Override
    public void addLine(HologramLine line) {
        plugin.getHologramManager().addLine(this, line);
    }

    @Override
    public void addLines(List<String> lines) {
        addTextLines(lines);
    }

    @Override
    public void addLines(Collection<? extends HologramLine> lines) {
        plugin.getHologramManager().addLines(this, lines);
    }

    @Override
    public void addTextLine(String line) {
        plugin.getHologramManager().addTextLine(this, line);
    }

    @Override
    public void addTextLines(Collection<String> lines) {
        plugin.getHologramManager().addTextLines(this, lines);
    }

    @Override
    public int getDefaultPageIndex() {
        return defaultPageIndex;
    }

    @Override
    public void setDefaultPageIndex(int index) {
        if (index < 0 || index >= pages.size()) {
            return;
        }

        this.defaultPageIndex = index;
        for (PlayerHologramData data : viewers.values()) {
            data.setCurrentPageIndex(index);
        }

        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public void setPermission(String permission) {
        this.permission = normalizePermission(permission);
        plugin.getHologramManager().saveHologram(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getSchedulerUtil().runAtEntity(player, () -> updateVisibility(player, false));
        }
    }

    @Override
    public VisibilityMode getVisibilityMode() {
        return visibilityMode;
    }

    @Override
    public void setVisibilityMode(VisibilityMode visibilityMode) {
        this.visibilityMode = visibilityMode == null ? VisibilityMode.ALL : visibilityMode;
        plugin.getHologramManager().saveHologram(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getSchedulerUtil().runAtEntity(player, () -> updateVisibility(player, false));
        }
    }

    @Override
    public int getViewDistance() {
        return viewDistance;
    }

    @Override
    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance <= 0 ? -1 : viewDistance;
        plugin.getHologramManager().saveHologram(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getSchedulerUtil().runAtEntity(player, () -> updateVisibility(player, false));
        }
    }

    @Override
    public float getScale() {
        return Math.max(scale, Math.max(scaleY, scaleZ));
    }

    @Override
    public void setScale(float scale) {
        float normalized = scale <= 0.0F ? 1.0F : scale;
        this.scale = normalized;
        this.scaleY = normalized;
        this.scaleZ = normalized;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public double getHeight() {
        return plugin.getHologramManager().getHeight(this);
    }

    @Override
    public double getHeight(int pageIndex) {
        return plugin.getHologramManager().getHeight(this, pageIndex);
    }

    @Override
    public double getLineHeight(HologramLine line) {
        return plugin.getHologramManager().resolveLineHeight(this, line);
    }

    @Override
    public float getScaleX() {
        return scale;
    }

    @Override
    public float getScaleY() {
        return scaleY;
    }

    @Override
    public float getScaleZ() {
        return scaleZ;
    }

    @Override
    public void setScale(float scaleX, float scaleY, float scaleZ) {
        this.scale = scaleX <= 0.0F ? 1.0F : scaleX;
        this.scaleY = scaleY <= 0.0F ? 1.0F : scaleY;
        this.scaleZ = scaleZ <= 0.0F ? 1.0F : scaleZ;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public Billboard getBillboard() {
        return billboard;
    }

    public void setBillboard(Billboard billboard) {
        this.billboard = billboard == null ? Billboard.CENTER : billboard;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public float getShadowStrength() {
        return shadowStrength;
    }

    @Override
    public void setShadowStrength(float shadowStrength) {
        this.shadowStrength = Math.max(0.0F, shadowStrength);
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public float getShadowRadius() {
        return shadowRadius;
    }

    @Override
    public void setShadowRadius(float shadowRadius) {
        this.shadowRadius = Math.max(0.0F, shadowRadius);
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public int getBrightnessBlock() {
        return brightnessBlock;
    }

    @Override
    public void setBrightnessBlock(int blockBrightness) {
        this.brightnessBlock = clampLight(blockBrightness);
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public int getBrightnessSky() {
        return brightnessSky;
    }

    @Override
    public void setBrightnessSky(int skyBrightness) {
        this.brightnessSky = clampLight(skyBrightness);
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public Color getBackgroundColor() {
        return backgroundColor;
    }

    @Override
    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public boolean hasTextShadow() {
        return textShadow;
    }

    @Override
    public void setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public boolean isSeeThrough() {
        return seeThrough;
    }

    @Override
    public void setSeeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public TextDisplay.TextAlignment getAlignment() {
        return alignment;
    }

    @Override
    public void setAlignment(TextDisplay.TextAlignment alignment) {
        this.alignment = alignment == null ? TextDisplay.TextAlignment.CENTER : alignment;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public long getUpdateTextInterval() {
        return updateTextInterval;
    }

    @Override
    public void setUpdateTextInterval(long updateTextInterval) {
        this.updateTextInterval = updateTextInterval <= 0L ? -1L : updateTextInterval;
        this.lastPeriodicRefreshTick = Long.MIN_VALUE;
        plugin.getHologramManager().saveHologram(this);
        plugin.getHologramManager().restartRefreshTask();
    }

    @Override
    public String getDisplayAnimation() {
        return displayAnimation;
    }

    @Override
    public void setDisplayAnimation(String displayAnimation) {
        this.displayAnimation = normalizeAnimationName(displayAnimation);
        this.displayAnimationEnabled = this.displayAnimation != null;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public boolean isDisplayAnimationEnabled() {
        return displayAnimationEnabled;
    }

    @Override
    public void setDisplayAnimationEnabled(boolean enabled) {
        this.displayAnimationEnabled = enabled;
        plugin.getHologramManager().saveHologram(this);
        refreshViewers();
    }

    @Override
    public String getEffectivePermission() {
        return permission == null || permission.isBlank() ? "axohologram.view." + id : permission;
    }

    @Override
    public String getLinkedNpc() {
        return linkedNpc;
    }

    @Override
    public void setLinkedNpc(String linkedNpc) {
        this.linkedNpc = linkedNpc == null || linkedNpc.isBlank() ? null : linkedNpc;
        plugin.getHologramManager().saveHologram(this);
    }

    @Override
    public List<HologramAction> getActions(HologramClickType clickType) {
        if (clickType == null) {
            return List.of();
        }
        List<HologramAction> clickActions = actions.get(clickType);
        return clickActions == null ? List.of() : Collections.unmodifiableList(clickActions);
    }

    @Override
    public void addAction(HologramClickType clickType, HologramAction action) {
        if (clickType == null || action == null) {
            return;
        }

        actions.computeIfAbsent(clickType, ignored -> new CopyOnWriteArrayList<>()).add(action);
        plugin.getHologramManager().saveHologram(this);
    }

    @Override
    public HologramAction removeAction(HologramClickType clickType, int index) {
        if (clickType == null || index < 0) {
            return null;
        }

        List<HologramAction> clickActions = actions.get(clickType);
        if (clickActions == null || index >= clickActions.size()) {
            return null;
        }

        HologramAction removed = clickActions.remove(index);
        if (clickActions.isEmpty()) {
            actions.remove(clickType);
        }
        plugin.getHologramManager().saveHologram(this);
        return removed;
    }

    @Override
    public void executeActions(Player player, HologramClickType clickType) {
        if (player == null || clickType == null) {
            return;
        }

        executeActionBranch(player, actions.get(HologramClickType.ANY));
        if (clickType != HologramClickType.ANY) {
            executeActionBranch(player, actions.get(clickType));
        }
    }

    private void executeActionBranch(Player player, List<HologramAction> clickActions) {
        if (clickActions == null || clickActions.isEmpty()) {
            return;
        }

        for (HologramAction action : new ArrayList<>(clickActions)) {
            HologramActionExecutor.execute(plugin, player, this, action);
        }
    }

    @Override
    public boolean setCurrentPage(Player player, int pageIndex) {
        if (player == null || !player.isOnline() || pageIndex < 0 || pageIndex >= pages.size()) {
            return false;
        }
        if (!pages.get(pageIndex).canView(player)) {
            return false;
        }

        PlayerHologramData data = viewers.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerHologramData(defaultPageIndex));
        data.setCurrentPageIndex(pageIndex);
        if (visibilityMode == VisibilityMode.MANUAL) {
            data.setManualVisible(true);
        }
        update(player);
        return true;
    }

    @Override
    public boolean changePage(Player player, int delta) {
        if (player == null || !player.isOnline() || delta == 0 || pages.isEmpty()) {
            return false;
        }

        PlayerHologramData data = viewers.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerHologramData(defaultPageIndex));
        int nextPage = findRelativeVisiblePage(player, data.getCurrentPageIndex(), delta);
        if (nextPage < 0) {
            return false;
        }

        data.setCurrentPageIndex(nextPage);
        if (visibilityMode == VisibilityMode.MANUAL) {
            data.setManualVisible(true);
        }
        update(player);
        return true;
    }

    @Override
    public void show(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PlayerHologramData data = viewers.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerHologramData(defaultPageIndex));
        if (visibilityMode == VisibilityMode.MANUAL) {
            data.setManualVisible(true);
        }

        plugin.getSchedulerUtil().runAtEntity(player, () -> showNow(player));
    }

    private void showNow(Player player) {
        if (!canViewNow(player)) {
            hide(player);
            return;
        }

        PlayerHologramData data = viewers.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerHologramData(defaultPageIndex));
        int pageIndex = resolveVisiblePageIndex(player, data.getCurrentPageIndex());
        if (pageIndex < 0) {
            hide(player);
            return;
        }

        HologramPage page = pages.get(pageIndex);
        data.setCurrentPageIndex(pageIndex);
        plugin.getHologramManager().onViewerAdded(player, this);
        renderPage(player, page, pageIndex, false);
    }

    @Override
    public void hide(Player player) {
        if (player != null) {
            PlayerHologramData data = viewers.remove(player.getUniqueId());
            if (data != null) {
                plugin.getHologramManager().onViewerRemoved(player, this);
                HologramPacketManager.destroyAllHologramLines(player.getUniqueId(), id);
            }
        }
    }

    @Override
    public void update(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        plugin.getSchedulerUtil().runAtEntity(player, () -> updateNow(player));
    }

    private void updateNow(Player player) {
        if (!canViewNow(player)) {
            hide(player);
            return;
        }

        PlayerHologramData data = viewers.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerHologramData(defaultPageIndex));
        int pageIndex = resolveVisiblePageIndex(player, data.getCurrentPageIndex());
        if (pageIndex < 0) {
            hide(player);
            return;
        }

        HologramPage page = pages.get(pageIndex);
        data.setCurrentPageIndex(pageIndex);
        plugin.getHologramManager().onViewerAdded(player, this);
        renderPage(player, page, pageIndex, true);
    }

    @Override
    public void destroy() {
        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hide(player);
            }
        }
        viewers.clear();
    }

    @Override
    public void refreshViewers() {
        if (viewers.isEmpty()) {
            return;
        }

        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                viewers.remove(uuid);
                continue;
            }
            PlayerHologramData data = viewers.get(uuid);
            if (data != null) {
                data.setDirty();
            }
            update(player);
        }
    }

    public void refreshDisplayAnimationViewers() {
        if (viewers.isEmpty()) {
            return;
        }

        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                viewers.remove(uuid);
                continue;
            }
            plugin.getSchedulerUtil().runAtEntity(player, () -> refreshDisplayAnimationNow(player));
        }
    }

    private void refreshDisplayAnimationNow(Player player) {
        if (!canViewNow(player)) {
            hide(player);
            return;
        }

        PlayerHologramData data = viewers.get(player.getUniqueId());
        if (data == null || data.isDirty()) {
            updateNow(player);
            return;
        }

        int pageIndex = resolveVisiblePageIndex(player, data.getCurrentPageIndex());
        if (pageIndex < 0) {
            hide(player);
            return;
        }

        HologramPage page = pages.get(pageIndex);
        if (pageIndex != data.getCurrentPageIndex() || data.isPageContentDirty(pageIndex, page)) {
            data.setCurrentPageIndex(pageIndex);
            updateNow(player);
            return;
        }

        if (!updateDisplayAnimationPage(player, page, pageIndex)) {
            updateNow(player);
        }
    }

    private boolean updateDisplayAnimationPage(Player player, HologramPage page, int pageIndex) {
        Location baseLocation = getLocation().add(offset);
        if (isSimpleTextPage(page)) {
            return HologramPacketManager.updateLineDisplayState(player, this, pageIndex, -1, baseLocation, billboard);
        }

        double currentYOffset = 0.0D;
        boolean updatedAllLines = true;
        for (int lineIndex = 0; lineIndex < page.getLines().size(); lineIndex++) {
            HologramLine line = page.getLines().get(lineIndex);
            if (!line.canView(player)) {
                line.destroy(player, id, pageIndex, lineIndex);
                continue;
            }

            Billboard effectiveBillboard = line.hasBillboardOverride() ? line.getBillboard() : billboard;
            HologramLine nextVisibleLine = findNextVisibleLine(player, page, lineIndex + 1);
            Location lineLocation = baseLocation.clone().add(
                    line.getOffset().getX(),
                    currentYOffset + line.getOffset().getY(),
                    line.getOffset().getZ()
            );
            updatedAllLines &= HologramPacketManager.updateLineDisplayState(player, this, pageIndex, lineIndex, lineLocation, effectiveBillboard);
            currentYOffset -= plugin.getHologramManager().resolveLineStep(this, line, nextVisibleLine);
        }
        return updatedAllLines;
    }

    @Override
    public boolean requiresPeriodicRefresh() {
        if (!enabled) {
            return false;
        }

        for (HologramPage page : pages) {
            for (HologramLine line : page.getLines()) {
                if (line instanceof TextLineImpl textLine && MiniMessageUtil.hasDynamicPlaceholders(textLine.getContent())) {
                    return true;
                }
                if (line instanceof ItemLineImpl itemLine && itemLine.requiresDynamicRefresh()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void updateVisibility(Player player, boolean force) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (force) {
            viewers.computeIfPresent(player.getUniqueId(), (p, data) -> {
                data.setDirty();
                return data;
            });
        }

        plugin.getSchedulerUtil().runAtEntity(player, () -> updateVisibilityNow(player));
    }

    private void updateVisibilityNow(Player player) {
        boolean shouldView = canViewNow(player);
        boolean viewing = isViewing(player);

        if (shouldView && !viewing) {
            showNow(player);
        } else if (!shouldView && viewing) {
            hide(player);
        } else if (shouldView) {
            updateNow(player);
        }
    }

    @Override
    public boolean isViewing(Player player) {
        return viewers.containsKey(player.getUniqueId());
    }

    @Override
    public boolean canView(Player player) {
        return canViewNow(player);
    }

    private boolean canViewNow(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (!enabled) {
            return false;
        }

        if (!isPlayerInWorldAndRange(player)) {
            return false;
        }

        return switch (visibilityMode) {
            case ALL -> true;
            case PERMISSION -> player.hasPermission(getEffectivePermission());
            case MANUAL -> {
                PlayerHologramData data = viewers.get(player.getUniqueId());
                yield data != null && data.isManualVisible();
            }
        };
    }

    @Override
    public void serialize(ConfigurationSection section) {
        Location currentLocation = location.clone();
        Vector currentOffset = offset.clone();

        section.set("enabled", enabled ? null : false);
        section.set("location.world", worldName);
        section.set("location.x", currentLocation.getX());
        section.set("location.y", currentLocation.getY());
        section.set("location.z", currentLocation.getZ());
        section.set("location.yaw", currentLocation.getYaw());
        section.set("location.pitch", currentLocation.getPitch());
        section.set("translation.x", currentOffset.getX());
        section.set("translation.y", currentOffset.getY());
        section.set("translation.z", currentOffset.getZ());
        section.set("permission", permission);
        section.set("visibility.mode", visibilityMode.name());
        section.set("visibility.distance", viewDistance > 0 ? viewDistance : null);
        section.set("scale.x", scale);
        section.set("scale.y", scaleY);
        section.set("scale.z", scaleZ);
        section.set("billboard", billboard.name());
        section.set("shadow.strength", shadowStrength);
        section.set("shadow.radius", shadowRadius);
        section.set("brightness.block", brightnessBlock >= 0 ? brightnessBlock : null);
        section.set("brightness.sky", brightnessSky >= 0 ? brightnessSky : null);
        section.set("style.background", backgroundColor != null ? ColorUtil.toHex(backgroundColor) : null);
        section.set("style.text-shadow", textShadow);
        section.set("style.see-through", seeThrough);
        section.set("style.alignment", alignment.name());
        section.set("text.update-interval", updateTextInterval > 0L ? updateTextInterval : null);
        section.set("display-animation-enabled", displayAnimationEnabled);
        section.set("display-animation", displayAnimation);
        section.set("default-page", defaultPageIndex + 1);
        section.set("linked-npc", linkedNpc);
        serializeActions(section);

        if (isSimpleTextHologram()) {
            section.set("type", "TEXT");
            section.set("text", collectSimpleText());
            section.set("pages", null);
            section.set("block", null);
            section.set("item", null);
            return;
        }

        if (isSimpleBlockHologram()) {
            section.set("type", "BLOCK");
            section.set("block", ((BlockLineImpl) pages.getFirst().getLine(0)).getContent());
            section.set("pages", null);
            section.set("text", null);
            section.set("item", null);
            return;
        }

        if (isSimpleItemHologram()) {
            section.set("type", "ITEM");
            section.set("item", ((ItemLineImpl) pages.getFirst().getLine(0)).getContent());
            section.set("pages", null);
            section.set("text", null);
            section.set("block", null);
            return;
        }

        List<Map<String, Object>> serializedPages = new ArrayList<>();
        for (HologramPage page : pages) {
            YamlConfiguration pageConfig = new YamlConfiguration();
            page.serialize(pageConfig);
            serializedPages.add(pageConfig.getValues(false));
        }
        section.set("pages", serializedPages);
    }

    public static Hologram deserialize(String id, ConfigurationSection section, AxoHologram plugin) {
        String worldName = section.getString("location.world");
        if (worldName == null || worldName.isBlank()) {
            plugin.getLogger().log(Level.SEVERE, "Hologram " + id + " has no world defined.");
            return null;
        }

        Location location = new Location(
                Bukkit.getWorld(worldName),
                section.getDouble("location.x"),
                section.getDouble("location.y"),
                section.getDouble("location.z"),
                (float) section.getDouble("location.yaw", 0.0D),
                (float) section.getDouble("location.pitch", 0.0D)
        );

        AxoHologramImpl hologram = new AxoHologramImpl(id, location, plugin);
        hologram.enabled = section.getBoolean("enabled", true);
        hologram.worldName = worldName;
        hologram.permission = normalizePermission(section.getString("permission"));
        hologram.linkedNpc = normalizeLinkedNpc(section.getString("linked-npc", section.getString("linkedNpc")));
        hologram.pages.clear();

        if (section.isConfigurationSection("translation") || section.contains("translation.x")) {
            hologram.offset = new Vector(
                    section.getDouble("translation.x", 0.0D),
                    section.getDouble("translation.y", 0.0D),
                    section.getDouble("translation.z", 0.0D)
            );
        } else if (section.isConfigurationSection("offset") || section.contains("offset.x")) {
            hologram.offset = new Vector(
                    section.getDouble("offset.x", 0.0D),
                    section.getDouble("offset.y", 0.0D),
                    section.getDouble("offset.z", 0.0D)
            );
        }

        if (section.contains("visibility.mode")) {
            hologram.visibilityMode = VisibilityMode.fromString(section.getString("visibility.mode"));
        } else if (section.contains("visibility")) {
            hologram.visibilityMode = VisibilityMode.fromString(section.getString("visibility"));
        } else if (hologram.permission != null) {
            hologram.visibilityMode = VisibilityMode.PERMISSION;
        }

        if (section.contains("visibility.distance")) {
            hologram.viewDistance = section.getInt("visibility.distance", -1);
        } else if (section.contains("visibility_distance")) {
            hologram.viewDistance = section.getInt("visibility_distance", -1);
        } else {
            hologram.viewDistance = section.getInt("view-distance", -1);
        }
        if (hologram.viewDistance <= 0) {
            hologram.viewDistance = -1;
        }

        if (section.contains("scale.x")) {
            hologram.scale = Math.max(0.01F, (float) section.getDouble("scale.x", 1.0D));
            hologram.scaleY = Math.max(0.01F, (float) section.getDouble("scale.y", hologram.scale));
            hologram.scaleZ = Math.max(0.01F, (float) section.getDouble("scale.z", hologram.scale));
        } else if (section.contains("scale_x")) {
            hologram.scale = Math.max(0.01F, (float) section.getDouble("scale_x", 1.0D));
            hologram.scaleY = Math.max(0.01F, (float) section.getDouble("scale_y", hologram.scale));
            hologram.scaleZ = Math.max(0.01F, (float) section.getDouble("scale_z", hologram.scale));
        } else {
            hologram.scale = Math.max(0.01F, (float) section.getDouble("scale", 1.0D));
            hologram.scaleY = hologram.scale;
            hologram.scaleZ = hologram.scale;
        }
        hologram.billboard = section.contains("billboard")
                ? Billboard.fromString(section.getString("billboard"))
                : Billboard.fromString(plugin.getConfigManager().getConfig().getString("general.defaults.billboard", "center"));
        hologram.shadowStrength = Math.max(0.0F, (float) readDouble(section, "shadow.strength", "shadow_strength", 1.0D));
        hologram.shadowRadius = Math.max(0.0F, (float) readDouble(section, "shadow.radius", "shadow_radius", 0.0D));
        hologram.brightnessBlock = section.contains("brightness.block") ? clampLight(section.getInt("brightness.block")) : -1;
        hologram.brightnessSky = section.contains("brightness.sky") ? clampLight(section.getInt("brightness.sky")) : -1;
        hologram.textShadow = section.contains("text_shadow")
                ? section.getBoolean("text_shadow")
                : section.getBoolean("style.text-shadow", false);
        hologram.seeThrough = section.contains("see_through")
                ? section.getBoolean("see_through")
                : section.getBoolean("style.see-through", false);
        hologram.alignment = section.contains("text_alignment")
                ? parseAlignment(section.getString("text_alignment"))
                : parseAlignment(section.getString("style.alignment"));
        hologram.updateTextInterval = readLong(section, -1L,
                "text.update-interval",
                "text_update_interval",
                "update-text-interval",
                "updateTextInterval");
        if (hologram.updateTextInterval <= 0L) {
            hologram.updateTextInterval = -1L;
        }
        String configuredDisplayAnimation = readString(section,
                "display-animation",
                "display.animation",
                "animation.display");
        boolean hasDisplayAnimation = configuredDisplayAnimation != null && !configuredDisplayAnimation.isBlank();
        boolean hasDisplayAnimationEnabled = containsAny(section,
                "display-animation-enabled",
                "display.animation.enabled",
                "animation.display-enabled");

        if (hasDisplayAnimation) {
            hologram.displayAnimation = normalizeAnimationName(configuredDisplayAnimation);
        } else if (!hasDisplayAnimationEnabled) {
            hologram.displayAnimation = null;
        }

        hologram.displayAnimationEnabled = hasDisplayAnimationEnabled
                ? readBoolean(section, hologram.displayAnimationEnabled,
                "display-animation-enabled",
                "display.animation.enabled",
                "animation.display-enabled")
                : hologram.displayAnimationEnabled;
        deserializeActions(section, hologram, plugin);

        String background = section.contains("background")
                ? section.getString("background")
                : section.getString("style.background");
        if (background != null && !background.isBlank()) {
            try {
                hologram.backgroundColor = ColorUtil.parseColor(background);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid background color for hologram '" + id + "': " + background);
            }
        }

        String simpleType = section.getString("type");
        if (simpleType != null && !simpleType.isBlank() && !section.contains("pages")) {
            if (deserializeSimpleRoot(hologram, section, plugin, simpleType)) {
                hologram.defaultPageIndex = 0;
                return hologram;
            }
        }

        List<Map<?, ?>> serializedPages = section.getMapList("pages");
        for (Map<?, ?> pageMap : serializedPages) {
            ConfigurationSection pageSection = new YamlConfiguration();
            pageMap.forEach((key, value) -> pageSection.set(String.valueOf(key), value));
            HologramPage page = AxoHologramPageImpl.deserialize(pageSection, plugin);
            if (page != null) {
                hologram.pages.add(page);
            }
        }

        if (hologram.pages.isEmpty()) {
            hologram.pages.add(new AxoHologramPageImpl());
        }

        hologram.defaultPageIndex = hologram.normalizePageIndex(section.getInt("default-page", 1) - 1);
        return hologram;
    }

    private void renderPage(Player player, HologramPage page, int pageIndex, boolean updateExisting) {
        PlayerHologramData data = viewers.get(player.getUniqueId());
        if (data == null) {
            return;
        }

        Location baseLocation = getLocation().add(offset);
        if (!updateExisting || data.isDirty() || data.isPageContentDirty(pageIndex, page)) {
            if (isSimpleTextPage(page)) {
                renderSimpleTextPage(player, page, pageIndex, baseLocation, updateExisting);
            } else {
                double currentYOffset = 0.0D;
                Map<Integer, HologramLine> visibleLines = new HashMap<>();
                for (int lineIndex = 0; lineIndex < page.getLines().size(); lineIndex++) {
                    HologramLine line = page.getLines().get(lineIndex);
                    if (!line.canView(player)) {
                        line.destroy(player, id, pageIndex, lineIndex);
                        continue;
                    }

                    Billboard effectiveBillboard = line.hasBillboardOverride() ? line.getBillboard() : billboard;
                    HologramLine nextVisibleLine = findNextVisibleLine(player, page, lineIndex + 1);
                    Location lineLocation = baseLocation.clone().add(
                            line.getOffset().getX(),
                            currentYOffset + line.getOffset().getY(),
                            line.getOffset().getZ()
                    );
                    if (updateExisting) {
                        line.update(player, this, pageIndex, lineIndex, lineLocation, effectiveBillboard);
                    } else {
                        line.spawn(player, this, pageIndex, lineIndex, lineLocation, effectiveBillboard);
                    }
                    visibleLines.put(lineIndex, line);
                    currentYOffset -= plugin.getHologramManager().resolveLineStep(this, line, nextVisibleLine);
                }

                HologramPacketManager.destroyLinesExcept(player, id, pageIndex, visibleLines.keySet());
                HologramPacketManager.destroyOtherPages(player, id, pageIndex);
            }

            data.setPageContent(pageIndex, page);
            data.setClean();
        }
    }

    private HologramLine findNextVisibleLine(Player player, HologramPage page, int startIndex) {
        for (int lineIndex = startIndex; lineIndex < page.getLines().size(); lineIndex++) {
            HologramLine line = page.getLines().get(lineIndex);
            if (line.canView(player)) {
                return line;
            }
        }
        return null;
    }

    private void renderSimpleTextPage(Player player, HologramPage page, int pageIndex, Location baseLocation, boolean updateExisting) {
        List<Component> lines = new ArrayList<>(page.getLines().size());
        for (HologramLine line : page.getLines()) {
            if (!(line instanceof TextLineImpl textLine)) {
                return;
            }

            String content = textLine.getContent();
            Component parsedLine;
            if (MiniMessageUtil.hasDynamicPlaceholders(content) || MiniMessageUtil.hasAnimationTags(content)) {
                parsedLine = MiniMessageUtil.parse(content, player);
            } else {
                parsedLine = staticLinesCache.computeIfAbsent(content, c -> MiniMessageUtil.parse(c, null));
            }
            lines.add(parsedLine);
        }

        Component combined = lines.isEmpty()
                ? Component.empty()
                : Component.join(JoinConfiguration.newlines(), lines);

        if (updateExisting) {
            HologramPacketManager.updateTextLine(player, this, pageIndex, -1, baseLocation, combined, billboard);
        } else {
            HologramPacketManager.spawnTextLine(player, this, pageIndex, -1, baseLocation, combined, billboard);
        }

        HologramPacketManager.destroyLinesExcept(player, id, pageIndex, java.util.Set.of(-1));
        HologramPacketManager.destroyOtherPages(player, id, pageIndex);
    }

    private boolean isSimpleTextPage(HologramPage page) {
        if (page.getLines().isEmpty()) {
            return false;
        }

        for (HologramLine line : page.getLines()) {
            if (!(line instanceof TextLineImpl textLine)) {
                return false;
            }
            if (textLine.hasBillboardOverride()) {
                return false;
            }
            if (textLine.hasHeightOverride()) {
                return false;
            }
            if (textLine.getPermission() != null && !textLine.getPermission().isBlank()) {
                return false;
            }
            if (textLine.getOffset().getX() != 0.0D || textLine.getOffset().getY() != 0.0D || textLine.getOffset().getZ() != 0.0D) {
                return false;
            }
        }

        return true;
    }

    private boolean isSimpleTextHologram() {
        return pages.size() == 1
                && pages.getFirst().getPermission() == null
                && isSimpleTextPage(pages.getFirst());
    }

    private boolean isSimpleBlockHologram() {
        return pages.size() == 1
                && pages.getFirst().getLines().size() == 1
                && pages.getFirst().getPermission() == null
                && pages.getFirst().getLine(0) instanceof BlockLineImpl blockLine
                && blockLine.getPermission() == null
                && !blockLine.hasBillboardOverride()
                && !blockLine.hasHeightOverride()
                && blockLine.getOffset().getX() == 0.0D
                && blockLine.getOffset().getY() == 0.0D
                && blockLine.getOffset().getZ() == 0.0D;
    }

    private boolean isSimpleItemHologram() {
        return pages.size() == 1
                && pages.getFirst().getLines().size() == 1
                && pages.getFirst().getPermission() == null
                && pages.getFirst().getLine(0) instanceof ItemLineImpl itemLine
                && itemLine.getPermission() == null
                && !itemLine.hasBillboardOverride()
                && !itemLine.hasHeightOverride()
                && itemLine.getOffset().getX() == 0.0D
                && itemLine.getOffset().getY() == 0.0D
                && itemLine.getOffset().getZ() == 0.0D;
    }

    private List<String> collectSimpleText() {
        List<String> textLines = new ArrayList<>(pages.getFirst().getLines().size());
        for (HologramLine line : pages.getFirst().getLines()) {
            textLines.add(((TextLineImpl) line).getContent());
        }
        return textLines;
    }

    private static boolean deserializeSimpleRoot(AxoHologramImpl hologram, ConfigurationSection section, AxoHologram plugin, String rawType) {
        HologramPage page = new AxoHologramPageImpl();
        switch (rawType.toUpperCase()) {
            case "TEXT" -> {
                List<String> text = section.getStringList("text");
                if (text.isEmpty()) {
                    return false;
                }
                for (String line : text) {
                    page.addLine(new TextLineImpl(line, plugin));
                }
            }
            case "BLOCK" -> {
                String block = section.getString("block");
                if (block == null || block.isBlank()) {
                    return false;
                }
                page.addLine(new BlockLineImpl(block, plugin));
            }
            case "ITEM" -> {
                String item = section.getString("item");
                if (item == null || item.isBlank()) {
                    return false;
                }
                page.addLine(new ItemLineImpl(item, plugin));
            }
            default -> {
                return false;
            }
        }

        hologram.pages.add(page);
        return true;
    }

    private static double readDouble(ConfigurationSection section, String primary, String fallback, double defaultValue) {
        if (section.contains(primary)) {
            return section.getDouble(primary, defaultValue);
        }
        if (section.contains(fallback)) {
            return section.getDouble(fallback, defaultValue);
        }
        return defaultValue;
    }

    private static long readLong(ConfigurationSection section, long defaultValue, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getLong(key, defaultValue);
            }
        }
        return defaultValue;
    }

    private static String readString(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getString(key);
            }
        }
        return null;
    }

    private static boolean readBoolean(ConfigurationSection section, boolean defaultValue, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getBoolean(key, defaultValue);
            }
        }
        return defaultValue;
    }

    private static boolean containsAny(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private void serializeActions(ConfigurationSection section) {
        if (actions.isEmpty()) {
            section.set("actions", null);
            return;
        }

        for (HologramClickType clickType : HologramClickType.values()) {
            List<HologramAction> clickActions = actions.get(clickType);
            if (clickActions == null || clickActions.isEmpty()) {
                section.set("actions." + clickType.getDisplayName(), null);
                continue;
            }

            List<Map<String, Object>> serialized = new ArrayList<>(clickActions.size());
            for (HologramAction action : clickActions) {
                YamlConfiguration actionConfig = new YamlConfiguration();
                action.serialize(actionConfig);
                serialized.add(new LinkedHashMap<>(actionConfig.getValues(true)));
            }
            section.set("actions." + clickType.getDisplayName(), serialized);
        }
    }

    private static void deserializeActions(ConfigurationSection section, AxoHologramImpl hologram, AxoHologram plugin) {
        for (HologramClickType clickType : HologramClickType.values()) {
            List<Map<?, ?>> serialized = section.getMapList("actions." + clickType.getDisplayName());
            if (serialized == null || serialized.isEmpty()) {
                continue;
            }

            List<HologramAction> clickActions = hologram.actions.computeIfAbsent(clickType, ignored -> new CopyOnWriteArrayList<>());
            for (Map<?, ?> actionMap : serialized) {
                ConfigurationSection actionSection = new YamlConfiguration();
                actionMap.forEach((key, value) -> actionSection.set(String.valueOf(key), value));
                HologramAction action = HologramAction.deserialize(actionSection);
                if (action != null) {
                    clickActions.add(action);
                } else {
                    plugin.getLogger().warning("Ignoring invalid " + clickType.getDisplayName() + " action on hologram '" + hologram.id + "'.");
                }
            }
        }
    }

    private boolean isPlayerInWorldAndRange(Player player) {
        resolveWorldIfNeeded();
        Location currentLocation = location;
        World world = currentLocation.getWorld();
        if (world == null || !world.equals(player.getWorld())) {
            return false;
        }

        int effectiveViewDistance = viewDistance > 0
                ? viewDistance
                : plugin.getConfigManager().getConfig().getInt("general.view-distance", 48);
        return currentLocation.distanceSquared(player.getLocation()) <= (double) effectiveViewDistance * effectiveViewDistance;
    }

    private void resolveWorldIfNeeded() {
        Location currentLocation = location;
        if (currentLocation.getWorld() == null && worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                location = new Location(
                        world,
                        currentLocation.getX(),
                        currentLocation.getY(),
                        currentLocation.getZ(),
                        currentLocation.getYaw(),
                        currentLocation.getPitch()
                );
            }
        }
    }

    private int normalizePageIndex(int pageIndex) {
        return clampPageIndex(pageIndex);
    }

    private int resolveVisiblePageIndex(Player player, int preferredPageIndex) {
        int normalized = normalizePageIndex(preferredPageIndex);
        if (pages.get(normalized).canView(player)) {
            return normalized;
        }

        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).canView(player)) {
                return i;
            }
        }
        return -1;
    }

    public boolean shouldPeriodicRefresh(long currentTick) {
        if (!requiresPeriodicRefresh()) {
            return false;
        }

        long effectiveInterval = updateTextInterval > 0L
                ? updateTextInterval
                : plugin.getConfigManager().getConfig().getLong("placeholders.refresh-interval", 20L);
        if (effectiveInterval <= 0L) {
            return false;
        }

        if (lastPeriodicRefreshTick == Long.MIN_VALUE || currentTick - lastPeriodicRefreshTick >= effectiveInterval) {
            lastPeriodicRefreshTick = currentTick;
            return true;
        }
        return false;
    }

    private int findRelativeVisiblePage(Player player, int currentPageIndex, int delta) {
        if (pages.isEmpty()) {
            return -1;
        }

        int direction = delta > 0 ? 1 : -1;
        int pageIndex = normalizePageIndex(currentPageIndex);
        for (int checked = 0; checked < pages.size(); checked++) {
            pageIndex = Math.floorMod(pageIndex + direction, pages.size());
            if (pages.get(pageIndex).canView(player)) {
                return pageIndex;
            }
        }
        return -1;
    }

    private int clampPageIndex(int pageIndex) {
        if (pages.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(pageIndex, pages.size() - 1));
    }

    private static int clampLight(int value) {
        if (value < 0) {
            return -1;
        }
        return Math.max(0, Math.min(value, 15));
    }

    private static TextDisplay.TextAlignment parseAlignment(String raw) {
        if (raw == null || raw.isBlank()) {
            return TextDisplay.TextAlignment.CENTER;
        }

        try {
            return TextDisplay.TextAlignment.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    private static String normalizePermission(String permission) {
        return permission == null || permission.isBlank() ? null : permission;
    }

    private static String normalizeLinkedNpc(String linkedNpc) {
        return linkedNpc == null || linkedNpc.isBlank() ? null : linkedNpc;
    }

    private static boolean readDefaultDisplayAnimationEnabled(AxoHologram plugin) {
        return plugin.getConfigManager().getConfig()
                .getBoolean("general.defaults.display-animation.enabled", false);
    }

    private static String readDefaultDisplayAnimationName(AxoHologram plugin) {
        String configured = plugin.getConfigManager().getConfig()
                .getString("general.defaults.display-animation.name", "cinematic_idle");
        return normalizeAnimationName(configured);
    }

    private static String normalizeAnimationName(String animationName) {
        return animationName == null || animationName.isBlank() ? null : animationName.trim();
    }

    private static final class PlayerHologramData {
        private volatile int currentPageIndex;
        private volatile boolean manualVisible;
        private volatile boolean dirty = true;
        private final Map<Integer, HologramPage> pageContent = new ConcurrentHashMap<>();

        private PlayerHologramData(int currentPageIndex) {
            this.currentPageIndex = currentPageIndex;
            this.manualVisible = false;
        }

        private int getCurrentPageIndex() {
            return currentPageIndex;
        }

        private void setCurrentPageIndex(int currentPageIndex) {
            if (this.currentPageIndex != currentPageIndex) {
                this.currentPageIndex = currentPageIndex;
                this.dirty = true;
            }
        }

        private boolean isManualVisible() {
            return manualVisible;
        }

        private void setManualVisible(boolean manualVisible) {
            this.manualVisible = manualVisible;
            this.dirty = true;
        }

        private boolean isDirty() {
            return dirty;
        }

        private void setDirty() {
            this.dirty = true;
        }

        private void setClean() {
            this.dirty = false;
        }

        private boolean isPageContentDirty(int pageIndex, HologramPage page) {
            return !Objects.equals(page, pageContent.get(pageIndex));
        }

        private void setPageContent(int pageIndex, HologramPage page) {
            pageContent.put(pageIndex, page);
        }
    }
}
