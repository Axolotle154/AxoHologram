package org.axostudio.axohologram.hologram;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.factory.HologramFactory;
import org.axostudio.axohologram.hologram.impl.AxoHologramImpl;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.line.impl.BlockLineImpl;
import org.axostudio.axohologram.hologram.line.impl.ItemLineImpl;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.hologram.page.impl.AxoHologramPageImpl;
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
    private final File hologramFolder;
    private SchedulerUtil.TaskHandle visibilityTask;
    private SchedulerUtil.TaskHandle refreshTask;

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

    private void refreshOnlineViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateVisibilityForPlayer(player, true);
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
        rebuildRuntimeCaches();
        startRefreshTask();
        if (visibilityTrackedHolograms.isEmpty() && visibilityTask != null) {
            visibilityTask.cancel();
            visibilityTask = null;
        }
        if (visibilityTask == null && !visibilityTrackedHolograms.isEmpty()) {
            startVisibilityTask();
        }
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
            startRefreshTask();
        }
    }

    public void onViewerRemoved(Player player, Hologram hologram) {
        if (player == null || hologram == null) {
            return;
        }
        activeHolograms.remove(player.getUniqueId(), hologram);
        if (periodicRefreshHolograms.contains(hologram) && !hasActiveViewers(hologram)) {
            activePeriodicRefreshHolograms.remove(hologram);
            stopRefreshTaskIfIdle();
        }
    }

    private void startTasks() {
        rebuildRuntimeCaches();
        startVisibilityTask();
        startRefreshTask();
        if (plugin.getAnimationManager() != null) {
            plugin.getAnimationManager().refreshTickTaskState();
        }
    }

    private void startVisibilityTask() {
        long interval = plugin.getConfigManager().getConfig().getLong(
                "performance.visibility-check-interval-ticks",
                plugin.getConfigManager().getConfig().getLong(
                        "performance.visibility-refresh-interval-ticks",
                        plugin.getConfigManager().getConfig().getLong(
                                "performance.visibility-refresh-interval",
                                plugin.getConfigManager().getConfig().getLong("general.visibility-check-interval", 20L)
                        )
                )
        );
        if (interval <= 0L) {
            return;
        }
        if (visibilityTrackedHolograms.isEmpty()) {
            return;
        }

        visibilityTask = plugin.getSchedulerUtil().runGlobalAtFixedRate(() -> {
            if (visibilityTrackedHolograms.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }

            long currentTick = Bukkit.getCurrentTick();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (shouldRecheckVisibility(player, currentTick)) {
                    updateVisibilityForPlayer(player, false, currentTick);
                }
            }
        }, interval, interval);
    }

    private void startRefreshTask() {
        if (refreshTask != null) {
            return;
        }
        Collection<Hologram> refreshTargets = getRefreshLoopHolograms();
        if (refreshTargets.isEmpty()) {
            return;
        }

        long interval = resolveRefreshSchedulerInterval();
        if (interval <= 0L) {
            return;
        }

        refreshTask = plugin.getSchedulerUtil().runGlobalAtFixedRate(() -> {
            Collection<Hologram> loopTargets = getRefreshLoopHolograms();
            if (loopTargets.isEmpty()) {
                stopRefreshTaskIfIdle();
                return;
            }
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                if (shouldSkipRefreshWhenNoViewers()) {
                    activePeriodicRefreshHolograms.clear();
                }
                stopRefreshTaskIfIdle();
                return;
            }

            long currentTick = Bukkit.getCurrentTick();
            for (Hologram hologram : loopTargets) {
                if (shouldSkipRefreshWhenNoViewers() && !hasActiveViewers(hologram)) {
                    activePeriodicRefreshHolograms.remove(hologram);
                    continue;
                }
                if (!hologram.requiresPeriodicRefresh()) {
                    periodicRefreshHolograms.remove(hologram);
                    activePeriodicRefreshHolograms.remove(hologram);
                    continue;
                }
                if (hologram instanceof AxoHologramImpl axoHologram && !axoHologram.shouldPeriodicRefresh(currentTick)) {
                    continue;
                }
                if (hologram instanceof AxoHologramImpl axoHologram) {
                    axoHologram.refreshDynamicViewers(true);
                } else {
                    hologram.refreshViewers();
                }
            }
            stopRefreshTaskIfIdle();
        }, interval, interval);
    }

    private long resolveRefreshSchedulerInterval() {
        if (periodicRefreshHolograms.isEmpty()) {
            return -1L;
        }

        long interval = plugin.getConfigManager().getConfig().getLong(
                "performance.dynamic-refresh-interval-ticks",
                plugin.getConfigManager().getConfig().getLong(
                        "performance.dynamic-line-update-interval-ticks",
                        plugin.getConfigManager().getConfig().getLong("placeholders.refresh-interval", 20L)
                )
        );
        if (interval <= 0L) {
            interval = Long.MAX_VALUE;
        }

        for (Hologram hologram : periodicRefreshHolograms) {
            long hologramInterval = hologram.getUpdateTextInterval();
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

        Location playerLocation = player.getLocation();
        String playerWorldName = playerLocation.getWorld() == null ? player.getWorld().getName() : playerLocation.getWorld().getName();
        for (Hologram hologram : visibilityTrackedHolograms) {
            if (!shouldProcessVisibility(hologram, player, playerWorldName, playerLocation, force)) {
                continue;
            }
            hologram.updateVisibility(player, force);
        }

        visibilityStates.put(player.getUniqueId(), new VisibilityState(playerLocation.clone(), currentTick));
    }

    public void handlePlayerMovement(Player player, Location to) {
        if (player == null || to == null || !player.isOnline() || holograms.isEmpty()) {
            return;
        }

        long currentTick = Bukkit.getCurrentTick();
        VisibilityState state = visibilityStates.get(player.getUniqueId());
        if (state == null || !Objects.equals(state.location().getWorld(), to.getWorld())) {
            updateVisibilityForPlayer(player, false, currentTick);
            return;
        }

        long cooldownTicks = plugin.getConfigManager().getConfig().getLong(
                "performance.movement-visibility-cooldown-ticks",
                plugin.getConfigManager().getConfig().getLong("performance.movement-visibility-cooldown", 5L)
        );
        if (cooldownTicks > 0L && currentTick - state.lastCheckTick() < cooldownTicks) {
            return;
        }

        if (!hasRelevantMovement(state.location(), to)) {
            return;
        }

        updateVisibilityForPlayer(player, false, currentTick);
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
                : plugin.getConfigManager().getConfig().getInt("general.view-distance", 48);
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

    private boolean hasRelevantMovement(Location previous, Location current) {
        MovementCheckMode mode = MovementCheckMode.fromConfig(plugin.getConfigManager().getConfig().getString("performance.movement-check-mode", "BLOCK"));
        return switch (mode) {
            case CHUNK -> previous.getBlockX() >> 4 != current.getBlockX() >> 4
                    || previous.getBlockZ() >> 4 != current.getBlockZ() >> 4;
            case DISTANCE -> hasMovedRequiredDistance(previous, current);
            case BLOCK -> previous.getBlockX() != current.getBlockX()
                    || previous.getBlockY() != current.getBlockY()
                    || previous.getBlockZ() != current.getBlockZ();
        };
    }

    private boolean hasMovedRequiredDistance(Location previous, Location current) {
        double threshold = plugin.getConfigManager().getConfig().getDouble("performance.visibility-move-distance-blocks", 1.5D);
        double thresholdSquared = threshold <= 0.0D ? 0.0D : threshold * threshold;
        return thresholdSquared == 0.0D || previous.distanceSquared(current) >= thresholdSquared;
    }

    public void clearVisibilityState(UUID playerId) {
        if (playerId != null) {
            visibilityStates.remove(playerId);
        }
    }

    private boolean shouldRecheckVisibility(Player player, long currentTick) {
        VisibilityState state = visibilityStates.get(player.getUniqueId());
        if (state == null || !Objects.equals(state.location().getWorld(), player.getWorld())) {
            return true;
        }

        long interval = plugin.getConfigManager().getConfig().getLong(
                "performance.visibility-check-interval-ticks",
                plugin.getConfigManager().getConfig().getLong(
                        "performance.visibility-refresh-interval-ticks",
                        plugin.getConfigManager().getConfig().getLong(
                                "performance.visibility-refresh-interval",
                                plugin.getConfigManager().getConfig().getLong("general.visibility-check-interval", 20L)
                        )
                )
        );
        return interval > 0L && currentTick - state.lastCheckTick() >= interval;
    }

    private record VisibilityState(Location location, long lastCheckTick) {
    }

    private enum MovementCheckMode {
        BLOCK,
        CHUNK,
        DISTANCE;

        private static MovementCheckMode fromConfig(String rawMode) {
            if (rawMode == null || rawMode.isBlank()) {
                return BLOCK;
            }
            try {
                return MovementCheckMode.valueOf(rawMode.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return BLOCK;
            }
        }
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
            visibilityTrackedHolograms.add(hologram);
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

    private Collection<Hologram> getRefreshLoopHolograms() {
        return shouldSkipRefreshWhenNoViewers() ? activePeriodicRefreshHolograms : periodicRefreshHolograms;
    }

    private boolean shouldSkipRefreshWhenNoViewers() {
        return plugin.getConfigManager().getConfig().getBoolean("performance.skip-refresh-when-no-viewers", true);
    }

    private boolean hasActiveViewers(Hologram hologram) {
        if (hologram instanceof AxoHologramImpl axoHologram) {
            return axoHologram.hasActiveViewers();
        }
        return true;
    }

    private void stopRefreshTaskIfIdle() {
        if (refreshTask != null && getRefreshLoopHolograms().isEmpty()) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void clearRuntimeCaches() {
        animatedHolograms.clear();
        dynamicPlaceholderHolograms.clear();
        periodicRefreshHolograms.clear();
        activePeriodicRefreshHolograms.clear();
        npcLinkedHolograms.clear();
        visibilityTrackedHolograms.clear();
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
        clearRuntimeCaches();
    }
}
