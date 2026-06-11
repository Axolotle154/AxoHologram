package org.axostudio.axohologram.hologram;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.config.VisibilityRuntimeConfig;
import org.axostudio.axohologram.hologram.factory.HologramFactory;
import org.axostudio.axohologram.hologram.impl.AxoHologramImpl;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.line.impl.BlockLineImpl;
import org.axostudio.axohologram.hologram.line.impl.ItemLineImpl;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.hologram.page.impl.AxoHologramPageImpl;
import org.axostudio.axohologram.media.MediaManager;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class HologramManager {

    public static final Pattern VALID_HOLOGRAM_ID = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final AxoHologram plugin;
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();
    private final Map<String, SchedulerUtil.TaskHandle> temporaryRemovalTasks = new ConcurrentHashMap<>();
    private final Map<UUID, VisibilityState> visibilityStates = new ConcurrentHashMap<>();
    private final Multimap<UUID, Hologram> activeHolograms = Multimaps.synchronizedSetMultimap(HashMultimap.create());
    private final Set<Hologram> animatedHolograms = ConcurrentHashMap.newKeySet();
    private final Set<Hologram> dynamicPlaceholderHolograms = ConcurrentHashMap.newKeySet();
    private final Set<Hologram> periodicRefreshHolograms = ConcurrentHashMap.newKeySet();
    private final Set<Hologram> activePeriodicRefreshHolograms = ConcurrentHashMap.newKeySet();
    private final Set<Hologram> npcLinkedHolograms = ConcurrentHashMap.newKeySet();
    private final Set<Hologram> visibilityTrackedHolograms = ConcurrentHashMap.newKeySet();
    private final Set<Hologram> visibilityUnindexedHolograms = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<Hologram>> visibilityWorldIndex = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Set<Hologram>>> visibilityChunkIndex = new ConcurrentHashMap<>();
    private final Map<Hologram, VisibilityIndexEntry> visibilityIndexEntries = new ConcurrentHashMap<>();
    private final File hologramFolder;
    private SchedulerUtil.TaskHandle visibilityTask;
    private SchedulerUtil.TaskHandle refreshTask;
    private volatile long refreshTaskIntervalTicks = -1L;
    private volatile int visibilityChunkSearchRadius = 1;

    public HologramManager(AxoHologram plugin) {
        this.plugin = plugin;
        this.hologramFolder = new File(plugin.getDataFolder(), "holograms");
        if (!hologramFolder.exists() && !hologramFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create hologram folder: " + hologramFolder.getAbsolutePath());
        }
    }

    public void loadHolograms() {
        stopTasks();
        cancelTemporaryRemovalTasks();
        holograms.clear();
        activeHolograms.clear();
        visibilityStates.clear();
        clearRuntimeCaches();
        plugin.getLogger().info("Loading holograms...");
        createDefaultAnimationExampleIfMissing();

        File[] files = hologramFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No hologram files found.");
            startTasks();
            return;
        }

        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            if (!isValidHologramId(id)) {
                plugin.getLogger().warning("Skipping hologram with invalid id '" + id + "' from file " + file.getName());
                continue;
            }

            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                if (MediaManager.isMediaConfiguration(config)) {
                    continue;
                }
                Hologram hologram = AxoHologramImpl.deserialize(id, config, plugin);
                if (hologram == null) {
                    plugin.getLogger().warning("Skipping hologram '" + id + "' because it could not be deserialized.");
                    continue;
                }

                holograms.put(id, hologram);
                plugin.getLogger().info("Loaded hologram: " + id);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load hologram from file: " + file.getName(), exception);
            }
        }

        plugin.getLogger().info(holograms.size() + " holograms loaded.");
        startTasks();
    }

    public synchronized void saveHolograms() {
        for (Hologram hologram : holograms.values()) {
            saveHologram(hologram);
        }
    }

    public synchronized void saveHologram(Hologram hologram) {
        if (hologram == null || !hologram.isPersistent()) {
            return;
        }

        File file = new File(hologramFolder, hologram.getId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        hologram.serialize(config);
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save hologram: " + hologram.getId(), exception);
        }
    }

    public Hologram createHologram(String id, Location location) {
        return createHologram(id, LineType.TEXT, location);
    }

    public Hologram createHologram(String id, LineType type, Location location) {
        return createHologram(id, type, location, true);
    }

    public Hologram createHologram(String id, LineType type, Location location, boolean persistent) {
        if (!isValidHologramId(id)) {
            return null;
        }
        if (location == null || location.getWorld() == null) {
            return null;
        }
        if (plugin.getMediaManager() != null && plugin.getMediaManager().getHologram(id) != null) {
            return null;
        }

        Hologram hologram = HologramFactory.create(id, type == null ? LineType.TEXT : type, location, plugin);
        hologram.setPersistent(persistent);
        if (holograms.putIfAbsent(id, hologram) != null) {
            return null;
        }
        saveHologram(hologram);
        restartRefreshTask();

        for (Player player : Bukkit.getOnlinePlayers()) {
            hologram.updateVisibility(player, false);
        }
        return hologram;
    }

    public Hologram createHologram(String id, Location location, List<String> lines, boolean persistent) {
        Hologram hologram = createHologram(id, LineType.TEXT, location, persistent);
        if (hologram == null) {
            return null;
        }

        setTextLines(hologram, lines);
        return hologram;
    }

    public Hologram createItemHologram(String id, Location location, String itemContent, boolean persistent) {
        ItemLineImpl itemLine = new ItemLineImpl(itemContent, plugin);
        Hologram hologram = createHologram(id, LineType.ITEM, location, persistent);
        if (hologram == null) {
            return null;
        }

        setLines(hologram, List.of(itemLine));
        return hologram;
    }

    public boolean deleteHologram(String id) {
        cancelTemporaryRemoval(id);
        Hologram hologram = holograms.remove(id);
        if (hologram == null) {
            return false;
        }

        hologram.destroy();
        if (hologram.isPersistent()) {
            try {
                Files.deleteIfExists(new File(hologramFolder, id + ".yml").toPath());
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete hologram file for " + id, exception);
            }
        }
        restartRefreshTask();
        return true;
    }

    public Hologram getHologram(String id) {
        return holograms.get(id);
    }

    public Collection<Hologram> getAllHolograms() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    public Collection<Hologram> getAnimatedHolograms() {
        return Collections.unmodifiableSet(animatedHolograms);
    }

    public boolean hasAnimatedHolograms() {
        return !animatedHolograms.isEmpty();
    }

    public Collection<Hologram> getDynamicPlaceholderHolograms() {
        return Collections.unmodifiableSet(dynamicPlaceholderHolograms);
    }

    public Collection<Hologram> getPeriodicRefreshHolograms() {
        return Collections.unmodifiableSet(periodicRefreshHolograms);
    }

    public Collection<Hologram> getNpcLinkedHolograms() {
        return Collections.unmodifiableSet(npcLinkedHolograms);
    }

    public synchronized boolean registerImportedHologram(Hologram hologram, boolean overwrite) {
        if (hologram == null || !isValidHologramId(hologram.getId())) {
            return false;
        }
        if (plugin.getMediaManager() != null && plugin.getMediaManager().getHologram(hologram.getId()) != null) {
            return false;
        }

        Hologram existing = holograms.get(hologram.getId());
        if (existing != null && !overwrite) {
            return false;
        }
        if (existing != null) {
            existing.destroy();
        }

        holograms.put(hologram.getId(), hologram);
        saveHologram(hologram);
        restartRefreshTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            hologram.updateVisibility(player, true);
        }
        return true;
    }

    public void destroyAllHolograms() {
        holograms.values().forEach(Hologram::destroy);
    }

    public void reload() {
        stopTasks();
        cancelTemporaryRemovalTasks();
        HologramPacketManager.destroyAllTrackedEntities();
        destroyAllHolograms();
        loadHolograms();
        plugin.getSchedulerUtil().runGlobalDelayed(task -> refreshOnlineViewers(), 1L);
    }

    public boolean reloadHologram(String id) {
        if (!isValidHologramId(id)) {
            return false;
        }

        File file = new File(hologramFolder, id + ".yml");
        if (!file.isFile()) {
            return false;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (MediaManager.isMediaConfiguration(config)) {
                return false;
            }

            Hologram reloaded = AxoHologramImpl.deserialize(id, config, plugin);
            if (reloaded == null) {
                return false;
            }

            Hologram previous = holograms.put(id, reloaded);
            if (previous != null) {
                previous.destroy();
            }
            restartRefreshTask();
            for (Player player : Bukkit.getOnlinePlayers()) {
                reloaded.updateVisibility(player, true);
            }
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload hologram: " + id, exception);
            return false;
        }
    }

    public void refreshRuntimeStateAndOnlineViewers() {
        restartRefreshTask();
        refreshOnlineViewers();
    }

    public void refreshOnlineViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getSchedulerUtil().runAtEntity(player, () -> updateVisibilityForPlayer(player, true));
        }
    }

    public void resetPlayerRenderState(Player player) {
        if (player == null) {
            return;
        }

        clearVisibilityState(player.getUniqueId());
        HologramPacketManager.destroyAllHologramLinesForPlayer(player);
        for (Hologram hologram : holograms.values()) {
            if (hologram instanceof AxoHologramImpl axoHologram) {
                axoHologram.resetViewerRenderState(player);
            }
        }
    }

    public void shutdown() {
        stopTasks();
        cancelTemporaryRemovalTasks();
        saveHolograms();
        HologramPacketManager.destroyAllTrackedEntities();
        destroyAllHolograms();
        visibilityStates.clear();
    }

    public void setTextLines(Hologram hologram, List<String> lines) {
        if (hologram == null || lines == null) {
            return;
        }

        HologramPage page = getOrCreateFirstPage(hologram);
        if (page == null) {
            return;
        }

        while (!page.getLines().isEmpty()) {
            page.removeLine(0);
        }
        for (String line : lines) {
            page.addLine(new TextLineImpl(line == null ? "" : line, plugin));
        }

        if (hologram instanceof AxoHologramImpl axoHologram) {
            axoHologram.markPeriodicRefreshStateDirty();
        }
        saveHologram(hologram);
        hologram.refreshViewers();
        restartRefreshTask();
    }

    public void addTextLine(Hologram hologram, String line) {
        addTextLines(hologram, List.of(line == null ? "" : line));
    }

    public void addLine(Hologram hologram, String line) {
        addTextLine(hologram, line);
    }

    public void setItemLine(Hologram hologram, String itemContent) {
        setLines(hologram, List.of(new ItemLineImpl(itemContent, plugin)));
    }

    public void addItemLine(Hologram hologram, String itemContent) {
        addLine(hologram, new ItemLineImpl(itemContent, plugin));
    }

    public void addTextLines(Hologram hologram, Collection<String> lines) {
        if (hologram == null || lines == null || lines.isEmpty()) {
            return;
        }

        List<HologramLine> textLines = new ArrayList<>(lines.size());
        for (String line : lines) {
            textLines.add(new TextLineImpl(line == null ? "" : line, plugin));
        }
        addLines(hologram, textLines);
    }

    public void addLines(Hologram hologram, List<String> lines) {
        addTextLines(hologram, lines);
    }

    public void setLines(Hologram hologram, Collection<? extends HologramLine> lines) {
        if (hologram == null || lines == null) {
            return;
        }

        HologramPage page = getOrCreateFirstPage(hologram);
        if (page == null) {
            return;
        }

        while (!page.getLines().isEmpty()) {
            page.removeLine(0);
        }
        for (HologramLine line : lines) {
            if (line != null) {
                page.addLine(line);
            }
        }

        if (hologram instanceof AxoHologramImpl axoHologram) {
            axoHologram.markPeriodicRefreshStateDirty();
        }
        saveHologram(hologram);
        hologram.refreshViewers();
        restartRefreshTask();
    }

    public void addLine(Hologram hologram, HologramLine line) {
        if (line == null) {
            return;
        }
        addLines(hologram, List.of(line));
    }

    public void addLines(Hologram hologram, Collection<? extends HologramLine> lines) {
        if (hologram == null || lines == null || lines.isEmpty()) {
            return;
        }

        HologramPage page = getOrCreateFirstPage(hologram);
        if (page == null) {
            return;
        }

        boolean added = false;
        for (HologramLine line : lines) {
            if (line == null) {
                continue;
            }
            page.addLine(line);
            added = true;
        }

        if (!added) {
            return;
        }

        if (hologram instanceof AxoHologramImpl axoHologram) {
            axoHologram.markPeriodicRefreshStateDirty();
        }
        saveHologram(hologram);
        hologram.refreshViewers();
        restartRefreshTask();
    }

    public double getHeight(Hologram hologram) {
        if (hologram == null) {
            return 0.0D;
        }
        return getHeight(hologram, hologram.getDefaultPageIndex());
    }

    public double getHeight(Hologram hologram, int pageIndex) {
        if (hologram == null) {
            return 0.0D;
        }

        HologramPage page = hologram.getPage(pageIndex);
        if (page == null || page.getLines().isEmpty()) {
            return 0.0D;
        }

        List<HologramLine> lines = page.getLines();
        double height = 0.0D;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            HologramLine line = lines.get(lineIndex);
            HologramLine nextLine = lineIndex + 1 < lines.size() ? lines.get(lineIndex + 1) : null;
            height += nextLine == null
                    ? resolveLineHeight(hologram, line)
                    : resolveLineStep(hologram, line, nextLine);
        }
        return height;
    }

    public double resolveLineStep(Hologram hologram, HologramLine line, HologramLine nextLine) {
        double textLineSpacing = plugin.getConfigManager().getConfig().getDouble("general.defaults.line-spacing", 0.25D);
        double step = resolveLineHeight(hologram, line, textLineSpacing);
        if (line != null && nextLine != null && !line.hasHeightOverride() && !nextLine.hasHeightOverride()) {
            step = Math.max(step, resolveLineHeight(hologram, nextLine, textLineSpacing));
        }
        return step;
    }

    public double resolveLineHeight(Hologram hologram, HologramLine line) {
        double textLineSpacing = plugin.getConfigManager().getConfig().getDouble("general.defaults.line-spacing", 0.25D);
        return resolveLineHeight(hologram, line, textLineSpacing);
    }

    private double resolveLineHeight(Hologram hologram, HologramLine line, double textLineSpacing) {
        if (line == null) {
            return textLineSpacing;
        }
        if (line.hasHeightOverride()) {
            return Math.max(0.0D, line.getHeight());
        }

        return switch (line.getType()) {
            case ITEM -> resolveDefaultDisplayLineHeight(hologram, line, "general.defaults.item-line-height", 0.65D, textLineSpacing);
            case BLOCK -> resolveDefaultDisplayLineHeight(hologram, line, "general.defaults.block-line-height", 1.0D, textLineSpacing);
            case TEXT -> textLineSpacing;
        };
    }

    private double resolveDefaultDisplayLineHeight(Hologram hologram, HologramLine line, String path, double fallback, double textLineSpacing) {
        double configured = Math.max(0.0D, plugin.getConfigManager().getConfig().getDouble(path, fallback));
        double hologramScale = hologram == null ? 1.0D : Math.max(0.01D, hologram.getScaleY());
        double lineScale = resolveDisplayLineHeightScale(line);
        double scaledHeight = configured * hologramScale * lineScale;
        return Math.max(textLineSpacing, scaledHeight);
    }

    private double resolveDisplayLineHeightScale(HologramLine line) {
        if (line instanceof ItemLineImpl itemLine) {
            return Math.max(0.01D, itemLine.getScaleY());
        }
        if (line instanceof BlockLineImpl blockLine) {
            return Math.max(0.01D, blockLine.getScaleY());
        }
        return 1.0D;
    }

    private HologramPage getOrCreateFirstPage(Hologram hologram) {
        HologramPage page = hologram.getPage(0);
        if (page != null) {
            return page;
        }

        page = new AxoHologramPageImpl();
        hologram.addPage(page);
        return page;
    }

    public void scheduleTemporaryRemoval(String id, long durationTicks) {
        if (durationTicks <= 0L || id == null || id.isBlank()) {
            return;
        }

        cancelTemporaryRemoval(id);
        SchedulerUtil.TaskHandle task = plugin.getSchedulerUtil().runGlobalDelayed(ignored -> deleteHologram(id), durationTicks);
        temporaryRemovalTasks.put(id, task);
    }

    public void restartRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        refreshTaskIntervalTicks = -1L;
        rebuildRuntimeCaches();
        refreshRefreshTaskState();
        refreshVisibilityTaskState();
        if (plugin.getAnimationManager() != null) {
            plugin.getAnimationManager().refreshTickTaskState();
        }
    }

    public boolean isValidHologramId(String id) {
        return id != null && VALID_HOLOGRAM_ID.matcher(id).matches();
    }

    private void cancelTemporaryRemoval(String id) {
        SchedulerUtil.TaskHandle task = temporaryRemovalTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelTemporaryRemovalTasks() {
        for (SchedulerUtil.TaskHandle task : temporaryRemovalTasks.values()) {
            task.cancel();
        }
        temporaryRemovalTasks.clear();
    }

    public void onViewerAdded(Player player, Hologram hologram) {
        if (player == null || hologram == null) {
            return;
        }
        activeHolograms.put(player.getUniqueId(), hologram);
        if (periodicRefreshHolograms.contains(hologram)) {
            activePeriodicRefreshHolograms.add(hologram);
            refreshRefreshTaskState();
        }
    }

    public void onViewerRemoved(Player player, Hologram hologram) {
        if (player == null || hologram == null) {
            return;
        }
        activeHolograms.remove(player.getUniqueId(), hologram);
        if (periodicRefreshHolograms.contains(hologram) && !hasActiveViewers(hologram)) {
            activePeriodicRefreshHolograms.remove(hologram);
            refreshRefreshTaskState();
        }
    }

    private void startTasks() {
        rebuildRuntimeCaches();
        refreshVisibilityTaskState();
        refreshRefreshTaskState();
        if (plugin.getAnimationManager() != null) {
            plugin.getAnimationManager().refreshTickTaskState();
        }
    }

    private void startVisibilityTask() {
        if (visibilityTask != null || !isPeriodicVisibilityTaskEnabled()) {
            return;
        }

        long interval = resolveVisibilityCheckInterval();
        if (interval <= 0L) {
            return;
        }
        if (visibilityTrackedHolograms.isEmpty()) {
            return;
        }

        visibilityTask = plugin.getSchedulerUtil().runGlobalAtFixedRate(() -> {
            if (!isPeriodicVisibilityTaskEnabled() || visibilityTrackedHolograms.isEmpty()) {
                cancelVisibilityTask();
                return;
            }
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }

            long currentTick = Bukkit.getCurrentTick();
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getSchedulerUtil().runAtEntity(player, () -> {
                    if (shouldRecheckVisibility(player, currentTick)) {
                        updateVisibilityForPlayer(player, false, currentTick);
                    }
                });
            }
        }, interval, interval);
    }

    private void refreshVisibilityTaskState() {
        if (!isPeriodicVisibilityTaskEnabled() || visibilityTrackedHolograms.isEmpty()) {
            cancelVisibilityTask();
            return;
        }
        startVisibilityTask();
    }

    private void cancelVisibilityTask() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
            visibilityTask = null;
        }
    }

    private boolean isPeriodicVisibilityTaskEnabled() {
        return plugin.getConfigManager().getVisibilityRuntimeConfig().periodicVisibilityTaskEnabled();
    }

    private long resolveVisibilityCheckInterval() {
        return plugin.getConfigManager().getVisibilityRuntimeConfig().visibilityCheckIntervalTicks();
    }

    private void startRefreshTask() {
        if (!hasVisibleDynamicRefreshWork()) {
            return;
        }

        long interval = resolveRefreshSchedulerInterval();
        if (interval <= 0L) {
            return;
        }
        if (refreshTask != null) {
            if (refreshTaskIntervalTicks == interval) {
                return;
            }
            refreshTask.cancel();
            refreshTask = null;
        }
        refreshTaskIntervalTicks = interval;

        refreshTask = plugin.getSchedulerUtil().runGlobalAtFixedRate(() -> {
            Collection<Hologram> loopTargets = getRefreshLoopHolograms();
            if (loopTargets.isEmpty()) {
                stopRefreshTaskIfIdle();
                return;
            }
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                activePeriodicRefreshHolograms.clear();
                stopRefreshTaskIfIdle();
                return;
            }

            long currentTick = Bukkit.getCurrentTick();
            boolean refreshTargetsChanged = false;
            for (Hologram hologram : loopTargets) {
                if (!hasActiveViewers(hologram)) {
                    refreshTargetsChanged |= activePeriodicRefreshHolograms.remove(hologram);
                    continue;
                }
                if (!hologram.requiresPeriodicRefresh()) {
                    periodicRefreshHolograms.remove(hologram);
                    refreshTargetsChanged |= activePeriodicRefreshHolograms.remove(hologram);
                    continue;
                }
                if (hologram instanceof AxoHologramImpl axoHologram && !axoHologram.shouldPeriodicRefresh(currentTick)) {
                    continue;
                }
                if (hologram instanceof AxoHologramImpl axoHologram) {
                    axoHologram.refreshDynamicViewers(true, true);
                } else {
                    hologram.refreshViewers();
                }
            }
            if (refreshTargetsChanged) {
                refreshRefreshTaskState();
            }
        }, interval, interval);
    }

    private long resolveRefreshSchedulerInterval() {
        Collection<Hologram> refreshTargets = getRefreshLoopHolograms();
        if (refreshTargets.isEmpty()) {
            return -1L;
        }

        long interval = Long.MAX_VALUE;
        for (Hologram hologram : refreshTargets) {
            long hologramInterval = hologram instanceof AxoHologramImpl axoHologram
                    ? axoHologram.getPeriodicRefreshIntervalTicks()
                    : hologram.getUpdateTextInterval();
            if (hologramInterval > 0L) {
                interval = Math.min(interval, hologramInterval);
            }
        }

        return interval == Long.MAX_VALUE ? -1L : interval;
    }

    public void updateVisibilityForPlayer(Player player, boolean force) {
        updateVisibilityForPlayer(player, force, Bukkit.getCurrentTick());
    }

    public void updateVisibilityForPlayer(Player player, boolean force, long currentTick) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        VisibilityState previousState = visibilityStates.get(playerId);
        if (previousState != null && previousState.lastCheckTick() == currentTick && (!force || previousState.forced())) {
            return;
        }

        Location playerLocation = player.getLocation();
        String playerWorldName = playerLocation.getWorld() == null ? player.getWorld().getName() : playerLocation.getWorld().getName();
        for (Hologram hologram : visibilityCandidatesFor(player, playerWorldName, playerLocation)) {
            if (!shouldProcessVisibility(hologram, player, playerWorldName, playerLocation, force)) {
                continue;
            }
            hologram.updateVisibility(player, force);
        }

        visibilityStates.put(playerId, VisibilityState.of(playerLocation, currentTick, force));
    }

    public void handlePlayerMovement(Player player, Location to) {
        if (player == null || to == null || !player.isOnline() || holograms.isEmpty()) {
            return;
        }

        long currentTick = Bukkit.getCurrentTick();
        VisibilityState state = visibilityStates.get(player.getUniqueId());
        if (state == null || !state.sameWorld(to)) {
            updateVisibilityForPlayer(player, false, currentTick);
            return;
        }

        long cooldownTicks = plugin.getConfigManager().getVisibilityRuntimeConfig().movementVisibilityCooldownTicks();
        if (cooldownTicks > 0L && currentTick - state.lastCheckTick() < cooldownTicks) {
            return;
        }

        if (!hasRelevantMovement(state, to)) {
            return;
        }

        updateVisibilityForPlayer(player, false, currentTick);
    }

    private Collection<Hologram> visibilityCandidatesFor(Player player, String playerWorldName, Location playerLocation) {
        Set<Hologram> candidates = new LinkedHashSet<>();
        candidates.addAll(activeHolograms.get(player.getUniqueId()));
        candidates.addAll(visibilityUnindexedHolograms);
        if (playerWorldName == null || playerWorldName.isBlank() || playerLocation == null) {
            return candidates;
        }

        Set<Hologram> worldHolograms = visibilityWorldIndex.get(playerWorldName);
        if (worldHolograms == null || worldHolograms.isEmpty()) {
            return candidates;
        }

        int radius = Math.max(1, visibilityChunkSearchRadius);
        long chunkScanCount = (long) (radius * 2 + 1) * (radius * 2 + 1);
        if (chunkScanCount > Math.max(16L, worldHolograms.size() * 2L)) {
            candidates.addAll(worldHolograms);
            return candidates;
        }

        Map<Long, Set<Hologram>> worldChunks = visibilityChunkIndex.get(playerWorldName);
        if (worldChunks == null || worldChunks.isEmpty()) {
            candidates.addAll(worldHolograms);
            return candidates;
        }

        int playerChunkX = playerLocation.getBlockX() >> 4;
        int playerChunkZ = playerLocation.getBlockZ() >> 4;
        for (int chunkX = playerChunkX - radius; chunkX <= playerChunkX + radius; chunkX++) {
            for (int chunkZ = playerChunkZ - radius; chunkZ <= playerChunkZ + radius; chunkZ++) {
                Set<Hologram> chunkHolograms = worldChunks.get(chunkKey(chunkX, chunkZ));
                if (chunkHolograms != null && !chunkHolograms.isEmpty()) {
                    candidates.addAll(chunkHolograms);
                }
            }
        }
        return candidates;
    }

    private boolean shouldProcessVisibility(Hologram hologram, Player player, String playerWorldName, Location playerLocation, boolean force) {
        if (hologram == null) {
            return false;
        }

        boolean viewing = hologram.isViewing(player);
        if (!hologram.isEnabled()) {
            return viewing;
        }

        boolean sameWorld = Objects.equals(hologram.getWorldName(), playerWorldName);
        if (!sameWorld) {
            return viewing;
        }

        boolean withinRange = isWithinQuickVisibilityRange(hologram, playerLocation);
        if (!withinRange) {
            return viewing;
        }

        if (force || !viewing) {
            return true;
        }

        return hologram instanceof AxoHologramImpl axoHologram && axoHologram.needsVisibilityRefresh(player);
    }

    private boolean isWithinQuickVisibilityRange(Hologram hologram, Location playerLocation) {
        if (playerLocation == null || playerLocation.getWorld() == null) {
            return false;
        }

        Location hologramLocation = hologram.getLocation();
        if (hologramLocation.getWorld() == null || !hologramLocation.getWorld().equals(playerLocation.getWorld())) {
            return false;
        }

        int effectiveViewDistance = hologram.getViewDistance() > 0
                ? hologram.getViewDistance()
                : plugin.getConfigManager().getVisibilityRuntimeConfig().defaultViewDistance();
        int chunkRadius = Math.max(1, (int) Math.ceil(effectiveViewDistance / 16.0D) + 1);
        int hologramChunkX = hologramLocation.getBlockX() >> 4;
        int hologramChunkZ = hologramLocation.getBlockZ() >> 4;
        int playerChunkX = playerLocation.getBlockX() >> 4;
        int playerChunkZ = playerLocation.getBlockZ() >> 4;
        if (Math.abs(hologramChunkX - playerChunkX) > chunkRadius || Math.abs(hologramChunkZ - playerChunkZ) > chunkRadius) {
            return false;
        }

        return hologramLocation.distanceSquared(playerLocation) <= (double) effectiveViewDistance * effectiveViewDistance;
    }

    private boolean hasRelevantMovement(VisibilityState previous, Location current) {
        VisibilityRuntimeConfig config = plugin.getConfigManager().getVisibilityRuntimeConfig();
        return switch (config.movementCheckMode()) {
            case CHUNK -> previous.chunkX() != current.getBlockX() >> 4
                    || previous.chunkZ() != current.getBlockZ() >> 4;
            case DISTANCE -> config.hasMovedRequiredDistance(
                    previous.location().getX(),
                    previous.location().getY(),
                    previous.location().getZ(),
                    current.getX(),
                    current.getY(),
                    current.getZ()
            );
            case BLOCK -> previous.blockX() != current.getBlockX()
                    || previous.blockY() != current.getBlockY()
                    || previous.blockZ() != current.getBlockZ();
        };
    }

    public void clearVisibilityState(UUID playerId) {
        if (playerId != null) {
            visibilityStates.remove(playerId);
        }
    }

    private boolean shouldRecheckVisibility(Player player, long currentTick) {
        if (!isPeriodicVisibilityTaskEnabled()) {
            return false;
        }

        VisibilityState state = visibilityStates.get(player.getUniqueId());
        if (state == null || !state.sameWorld(player.getLocation())) {
            return true;
        }

        long interval = resolveVisibilityCheckInterval();
        return interval > 0L && currentTick - state.lastCheckTick() >= interval;
    }

    private record VisibilityState(
            Location location,
            long lastCheckTick,
            boolean forced,
            int blockX,
            int blockY,
            int blockZ,
            int chunkX,
            int chunkZ
    ) {
        private static VisibilityState of(Location location, long lastCheckTick, boolean forced) {
            Location snapshot = location.clone();
            return new VisibilityState(
                    snapshot,
                    lastCheckTick,
                    forced,
                    snapshot.getBlockX(),
                    snapshot.getBlockY(),
                    snapshot.getBlockZ(),
                    snapshot.getBlockX() >> 4,
                    snapshot.getBlockZ() >> 4
            );
        }

        private boolean sameWorld(Location other) {
            return other != null && Objects.equals(location.getWorld(), other.getWorld());
        }
    }

    private record VisibilityIndexEntry(String worldName, int chunkX, int chunkZ) {
    }

    public void rebuildRuntimeCaches() {
        clearRuntimeCaches();
        for (Hologram hologram : holograms.values()) {
            if (hologram instanceof AxoHologramImpl axoHologram) {
                axoHologram.markPeriodicRefreshStateDirty();
            }
            registerRuntimeState(hologram);
        }
    }

    private void registerRuntimeState(Hologram hologram) {
        if (hologram == null) {
            return;
        }

        if (isLinkedToNpc(hologram)) {
            npcLinkedHolograms.add(hologram);
        }

        if (hologram.isEnabled()) {
            refreshVisibilityIndex(hologram);
        }

        if (hologram.requiresPeriodicRefresh()) {
            periodicRefreshHolograms.add(hologram);
            if (hasActiveViewers(hologram)) {
                activePeriodicRefreshHolograms.add(hologram);
            }
            if (hologram instanceof AxoHologramImpl axoHologram && axoHologram.hasDynamicPlaceholderRefresh()) {
                dynamicPlaceholderHolograms.add(hologram);
            }
        }

        if (plugin.getAnimationManager() != null && plugin.getAnimationManager().hasAnimatedContent(hologram)) {
            animatedHolograms.add(hologram);
        }
    }

    private boolean isLinkedToNpc(Hologram hologram) {
        String linkedNpc = hologram.getLinkedNpc();
        return linkedNpc != null && !linkedNpc.isBlank();
    }

    public void refreshVisibilityIndex(Hologram hologram) {
        if (hologram == null) {
            return;
        }

        visibilityTrackedHolograms.remove(hologram);
        visibilityUnindexedHolograms.remove(hologram);
        unregisterVisibilityIndex(hologram);
        if (!hologram.isEnabled()) {
            return;
        }

        visibilityTrackedHolograms.add(hologram);
        VisibilityIndexEntry entry = createVisibilityIndexEntry(hologram);
        if (entry == null) {
            visibilityUnindexedHolograms.add(hologram);
            return;
        }

        visibilityIndexEntries.put(hologram, entry);
        visibilityWorldIndex.computeIfAbsent(entry.worldName(), ignored -> ConcurrentHashMap.newKeySet()).add(hologram);
        visibilityChunkIndex
                .computeIfAbsent(entry.worldName(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey(entry.chunkX(), entry.chunkZ()), ignored -> ConcurrentHashMap.newKeySet())
                .add(hologram);
        visibilityChunkSearchRadius = Math.max(visibilityChunkSearchRadius, resolveVisibilityChunkRadius(hologram));
    }

    private void unregisterVisibilityIndex(Hologram hologram) {
        VisibilityIndexEntry previous = visibilityIndexEntries.remove(hologram);
        if (previous == null) {
            return;
        }

        removeFromWorldIndex(previous.worldName(), hologram);
        removeFromChunkIndex(previous, hologram);
    }

    private void removeFromWorldIndex(String worldName, Hologram hologram) {
        visibilityWorldIndex.computeIfPresent(worldName, (ignored, indexedHolograms) -> {
            indexedHolograms.remove(hologram);
            return indexedHolograms.isEmpty() ? null : indexedHolograms;
        });
    }

    private void removeFromChunkIndex(VisibilityIndexEntry entry, Hologram hologram) {
        visibilityChunkIndex.computeIfPresent(entry.worldName(), (ignored, worldChunks) -> {
            long key = chunkKey(entry.chunkX(), entry.chunkZ());
            worldChunks.computeIfPresent(key, (chunkKey, indexedHolograms) -> {
                indexedHolograms.remove(hologram);
                return indexedHolograms.isEmpty() ? null : indexedHolograms;
            });
            return worldChunks.isEmpty() ? null : worldChunks;
        });
    }

    private VisibilityIndexEntry createVisibilityIndexEntry(Hologram hologram) {
        Location location = hologram.getLocation();
        String worldName = location.getWorld() == null ? hologram.getWorldName() : location.getWorld().getName();
        if (worldName == null || worldName.isBlank() || location.getWorld() == null) {
            return null;
        }

        return new VisibilityIndexEntry(
                worldName,
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4
        );
    }

    private int resolveVisibilityChunkRadius(Hologram hologram) {
        int effectiveViewDistance = hologram.getViewDistance() > 0
                ? hologram.getViewDistance()
                : plugin.getConfigManager().getVisibilityRuntimeConfig().defaultViewDistance();
        return Math.max(1, (int) Math.ceil(Math.max(1, effectiveViewDistance) / 16.0D) + 1);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private Collection<Hologram> getRefreshLoopHolograms() {
        return activePeriodicRefreshHolograms;
    }

    private void refreshRefreshTaskState() {
        if (!hasVisibleDynamicRefreshWork()) {
            stopRefreshTaskIfIdle();
            return;
        }
        startRefreshTask();
    }

    private boolean hasVisibleDynamicRefreshWork() {
        if (activePeriodicRefreshHolograms.isEmpty()) {
            return false;
        }

        for (Hologram hologram : activePeriodicRefreshHolograms) {
            if (hologram == null || !hologram.isEnabled() || !hologram.requiresPeriodicRefresh() || !hasActiveViewers(hologram)) {
                activePeriodicRefreshHolograms.remove(hologram);
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean hasActiveViewers(Hologram hologram) {
        if (hologram instanceof AxoHologramImpl axoHologram) {
            return axoHologram.hasActiveViewers();
        }
        return true;
    }

    private void stopRefreshTaskIfIdle() {
        if (refreshTask != null && !hasVisibleDynamicRefreshWork()) {
            refreshTask.cancel();
            refreshTask = null;
            refreshTaskIntervalTicks = -1L;
        }
    }

    private void clearRuntimeCaches() {
        animatedHolograms.clear();
        dynamicPlaceholderHolograms.clear();
        periodicRefreshHolograms.clear();
        activePeriodicRefreshHolograms.clear();
        npcLinkedHolograms.clear();
        visibilityTrackedHolograms.clear();
        visibilityUnindexedHolograms.clear();
        visibilityWorldIndex.clear();
        visibilityChunkIndex.clear();
        visibilityIndexEntries.clear();
        visibilityChunkSearchRadius = 1;
    }

    private void createDefaultAnimationExampleIfMissing() {
        File exampleFile = new File(hologramFolder, "animation_example.yml");
        if (exampleFile.exists()) {
            return;
        }

        if (Bukkit.getWorlds().isEmpty()) {
            plugin.getLogger().warning("Could not create animation_example.yml because no worlds are loaded.");
            return;
        }

        World world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", false);
        config.set("location.world", world.getName());
        config.set("location.x", spawn.getX());
        config.set("location.y", spawn.getY() + 2.0D);
        config.set("location.z", spawn.getZ());
        config.set("location.yaw", 0.0D);
        config.set("location.pitch", 0.0D);
        config.set("visibility.mode", "ALL");
        config.set("display-animation-enabled", false);
        config.set("display-animation", "cinematic_idle");
        config.set("type", "TEXT");
        config.set("text", List.of(
                "&7AxoHologram animation example",
                "<anim:rainbow>Rainbow text</anim:rainbow>",
                "<anim:pulse_blue>Pulse blue</anim:pulse_blue>",
                "<anim:wave_aqua>Wave aqua</anim:wave_aqua>",
                "<anim:rainbow_cycle>Custom frame animation</anim:rainbow_cycle>"
        ));

        try {
            config.save(exampleFile);
            plugin.getLogger().info("Created disabled animation example hologram: animation_example.yml");
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create animation_example.yml", exception);
        }
    }

    private void stopTasks() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
            visibilityTask = null;
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        refreshTaskIntervalTicks = -1L;
        clearRuntimeCaches();
    }
}
