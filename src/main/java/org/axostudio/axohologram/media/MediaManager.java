package org.axostudio.axohologram.media;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class MediaManager {

    private final AxoHologram plugin;
    private final Map<String, MediaHologram> mediaHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, Set<MediaHologram>> mediaByViewer = new ConcurrentHashMap<>();
    private final Map<MediaHologram, SchedulerUtil.TaskHandle> unloadTasks = new ConcurrentHashMap<>();
    private final File hologramFolder;
    private final ExecutorService executor;
    private final MediaCacheManager cacheManager;
    private final MediaDownloader downloader;
    private final ThumbnailManager thumbnailManager;
    private final FrameProcessor frameProcessor;
    private final ImageRenderer imageRenderer;
    private final VideoRenderer videoRenderer;
    private SchedulerUtil.TaskHandle updateTask;
    private long updateTaskInterval;
    private volatile MediaRuntimeConfig runtimeConfig;
    private volatile List<MediaHologram> mediaSnapshot = List.of();
    private volatile List<MediaHologram> videoSnapshot = List.of();
    private volatile Map<String, List<MediaHologram>> mediaByWorldSnapshot = Map.of();
    private long lastVisibilityRefreshTick;

    public MediaManager(AxoHologram plugin, MediaCacheManager cacheManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.hologramFolder = new File(plugin.getDataFolder(), "holograms");
        this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), new MediaThreadFactory());
        this.downloader = new MediaDownloader(plugin, cacheManager);
        this.thumbnailManager = new ThumbnailManager();
        this.frameProcessor = new FrameProcessor(cacheManager, thumbnailManager);
        this.imageRenderer = new ImageRenderer(plugin);
        this.videoRenderer = new VideoRenderer(imageRenderer);
        this.runtimeConfig = MediaRuntimeConfig.from(plugin.getConfigManager().getMedia());
        cacheManager.ensureFolders();
        if (!hologramFolder.exists() && !hologramFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create hologram folder: " + hologramFolder.getAbsolutePath());
        }
    }

    public void loadMedia() {
        stopUpdateTask();
        destroyRuntime();
        mediaHolograms.clear();
        rebuildSnapshots();
        reloadRuntimeConfig();
        cacheManager.ensureFolders();

        if (!isSystemEnabled()) {
            plugin.getLogger().info("Media system is disabled.");
            return;
        }

        File[] files = hologramFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            startUpdateTask();
            return;
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        sortedFiles.sort(Comparator.comparing(File::getName));
        for (File file : sortedFiles) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                if (!isMediaConfiguration(config)) {
                    continue;
                }
                if (!plugin.getHologramManager().isValidHologramId(id)) {
                    plugin.getLogger().warning("Skipping media hologram with invalid id '" + id + "'.");
                    continue;
                }

                MediaHologram hologram = MediaHologram.deserialize(id, config, plugin);
                if (hologram == null) {
                    continue;
                }
                if (!isTypeEnabled(hologram.getType())) {
                    plugin.getLogger().warning("Skipping media hologram '" + id + "' because " + hologram.getType().name().toLowerCase() + " media is disabled.");
                    continue;
                }
                if (!canAddToWorld(hologram.getWorldName())) {
                    plugin.getLogger().warning("Skipping media hologram '" + id + "' because max-media-per-world was reached.");
                    continue;
                }

                mediaHolograms.put(id, hologram);
                prepareMedia(hologram);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load media hologram from file: " + file.getName(), exception);
            }
        }

        rebuildSnapshots();
        plugin.getLogger().info(mediaHolograms.size() + " media holograms loaded.");
        startUpdateTask();
    }

    public void reload() {
        loadMedia();
        plugin.getSchedulerUtil().runGlobalDelayed(task -> refreshOnlineViewers(), 1L);
    }

    public void shutdown() {
        stopUpdateTask();
        destroyRuntime();
        executor.shutdownNow();
        mediaHolograms.clear();
    }

    public CompletableFuture<MediaHologram> createImageHologram(String id, String rawUrl, Location location, MediaSettings options) {
        return createMediaHologram(id, rawUrl, location, options, MediaType.IMAGE);
    }

    public CompletableFuture<MediaHologram> createVideoHologram(String id, String rawUrl, Location location, MediaSettings options) {
        return createMediaHologram(id, rawUrl, location, options, MediaType.VIDEO);
    }

    private CompletableFuture<MediaHologram> createMediaHologram(String id, String rawUrl, Location location, MediaSettings options, MediaType type) {
        if (!isSystemEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Media system is disabled."));
        }
        if (!isTypeEnabled(type)) {
            return CompletableFuture.failedFuture(new IllegalStateException(type.name().toLowerCase() + " media is disabled."));
        }
        if (!plugin.getHologramManager().isValidHologramId(id)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid hologram id."));
        }
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("A valid location is required."));
        }
        if (!canAddToWorld(location.getWorld().getName())) {
            return CompletableFuture.failedFuture(new IllegalStateException("The world reached max-media-per-world."));
        }

        try {
            URI uri = new URI(rawUrl);
            MediaSettings settings = options == null ? MediaSettings.defaults(type, plugin.getConfigManager().getMedia()) : options;
            MediaHologram hologram = new MediaHologram(plugin, id, type, uri, location, settings);
            if (plugin.getHologramManager().getHologram(id) != null || mediaHolograms.containsKey(id)) {
                return CompletableFuture.failedFuture(new IllegalStateException("A hologram with this id already exists."));
            }
            if (mediaHolograms.putIfAbsent(id, hologram) != null) {
                return CompletableFuture.failedFuture(new IllegalStateException("A hologram with this id already exists."));
            }
            rebuildSnapshots();
            saveMediaHologram(hologram);
            startUpdateTask();
            return prepareMedia(hologram);
        } catch (URISyntaxException exception) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid media URL.", exception));
        }
    }

    public boolean removeHologram(String id) {
        MediaHologram hologram = mediaHolograms.remove(id);
        if (hologram == null) {
            return false;
        }

        rebuildSnapshots();
        cancelUnloadTask(hologram);
        imageRenderer.destroy(hologram);
        clearViewers(hologram);
        try {
            Files.deleteIfExists(new File(hologramFolder, id + ".yml").toPath());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete media hologram file for " + id, exception);
        }
        stopUpdateTaskIfIdle();
        return true;
    }

    public boolean reloadHologram(String id) {
        File file = new File(hologramFolder, id + ".yml");
        if (!file.isFile()) {
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!isMediaConfiguration(config)) {
            return false;
        }

        MediaHologram loaded = MediaHologram.deserialize(id, config, plugin);
        if (loaded == null) {
            return false;
        }

        MediaHologram previous = mediaHolograms.put(id, loaded);
        rebuildSnapshots();
        prepareMedia(loaded).whenComplete((media, throwable) -> {
            if (mediaHolograms.get(id) != loaded) {
                return;
            }
            if (throwable != null) {
                clearViewers(loaded);
                if (previous != null) {
                    mediaHolograms.put(id, previous);
                    rebuildSnapshots();
                    plugin.getSchedulerUtil().runGlobal(this::refreshOnlineViewers);
                }
                return;
            }
            if (previous != null) {
                cancelUnloadTask(previous);
                imageRenderer.destroy(previous);
                clearViewers(previous);
            }
        });
        startUpdateTask();
        refreshOnlineViewers();
        return true;
    }

    public void saveMediaHologram(MediaHologram hologram) {
        if (hologram == null) {
            return;
        }

        File file = new File(hologramFolder, hologram.getId() + ".yml");
        try {
            hologram.toYaml().save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save media hologram: " + hologram.getId(), exception);
        }
    }

    public MediaHologram getHologram(String id) {
        return mediaHolograms.get(id);
    }

    public Collection<MediaHologram> getAllMediaHolograms() {
        return mediaSnapshot;
    }

    public boolean hasMediaHolograms() {
        return !mediaHolograms.isEmpty();
    }

    public boolean isMediaDisplay(Entity entity) {
        if (!(entity instanceof ItemFrame itemFrame)) {
            return false;
        }
        if (itemFrame.getScoreboardTags().contains(ImageRenderer.MEDIA_FRAME_TAG)) {
            return true;
        }

        for (MediaHologram hologram : mediaSnapshot) {
            for (MediaMapTile tile : hologram.getMapTiles()) {
                ItemFrame display = tile.display();
                if (display != null && display.getUniqueId().equals(itemFrame.getUniqueId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean playVideo(String id) {
        MediaHologram hologram = mediaHolograms.get(id);
        if (hologram == null || hologram.getType() != MediaType.VIDEO || hologram.getState() != MediaState.READY) {
            return false;
        }

        hologram.setPlaybackState(VideoPlaybackState.PLAYING);
        hologram.setAutoPaused(false);
        hologram.setLastFrameTick(0L);
        if (hologram.hasViewers()) {
            videoRenderer.renderCurrentFrame(hologram);
        }
        startUpdateTask();
        return true;
    }

    public boolean pauseVideo(String id) {
        MediaHologram hologram = mediaHolograms.get(id);
        if (hologram == null || hologram.getType() != MediaType.VIDEO) {
            return false;
        }

        hologram.setPlaybackState(VideoPlaybackState.PAUSED);
        stopUpdateTaskIfIdle();
        return true;
    }

    public boolean stopVideo(String id) {
        MediaHologram hologram = mediaHolograms.get(id);
        if (hologram == null || hologram.getType() != MediaType.VIDEO) {
            return false;
        }

        hologram.setPlaybackState(VideoPlaybackState.STOPPED);
        hologram.setCurrentFrame(0);
        hologram.setLastFrameTick(0L);
        if (hologram.hasViewers()) {
            videoRenderer.renderCurrentFrame(hologram);
        }
        stopUpdateTaskIfIdle();
        return true;
    }

    public boolean moveHologram(String id, Location location) {
        MediaHologram hologram = mediaHolograms.get(id);
        if (hologram == null || location == null || location.getWorld() == null) {
            return false;
        }

        hologram.setLocation(location);
        rebuildSnapshots();
        saveMediaHologram(hologram);
        cancelUnloadTask(hologram);
        imageRenderer.destroy(hologram);
        clearViewers(hologram);
        refreshOnlineViewers();
        renderCurrentMedia(hologram);
        return true;
    }

    public CompletableFuture<MediaHologram> updateSettings(String id, MediaSettings settings) {
        MediaHologram hologram = mediaHolograms.get(id);
        if (hologram == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Media hologram not found."));
        }
        if (settings == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Media settings are required."));
        }

        hologram.setSettings(settings);
        hologram.setProcessedMedia(null);
        saveMediaHologram(hologram);
        cancelUnloadTask(hologram);
        imageRenderer.destroy(hologram);
        clearViewers(hologram);
        startUpdateTask();
        return prepareMedia(hologram);
    }

    public boolean updateRenderDistance(String id, int renderDistance) {
        MediaHologram hologram = mediaHolograms.get(id);
        if (hologram == null) {
            return false;
        }

        MediaSettings currentSettings = hologram.getSettings();
        if (currentSettings == null) {
            currentSettings = MediaSettings.defaults(hologram.getType(), plugin.getConfigManager().getMedia());
        }

        hologram.setSettings(currentSettings.withRenderDistance(renderDistance));
        saveMediaHologram(hologram);
        refreshOnlineViewers();
        startUpdateTask();
        return true;
    }

    public void refreshOnlineViewers() {
        refreshOnlineViewers(false);
    }

    public void refreshPlayerVisibility(Player player) {
        refreshPlayerVisibility(player, false);
    }

    public boolean usesLineOfSightVisibility() {
        return runtimeConfig.lineOfSightEnabled();
    }

    public long visibilityRefreshIntervalTicks() {
        return runtimeConfig.visibilityCheckIntervalTicks();
    }

    private void refreshOnlineViewers(boolean dynamicOnly) {
        List<MediaHologram> targets = dynamicOnly ? videoSnapshot : mediaSnapshot;
        if (targets.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getSchedulerUtil().runAtEntity(player, () -> refreshPlayerVisibility(player, dynamicOnly));
        }
    }

    private void refreshPlayerVisibility(Player player, boolean dynamicOnly) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Set<MediaHologram> viewedMedia = mediaByViewer.get(playerId);
        if (viewedMedia != null && !viewedMedia.isEmpty()) {
            for (MediaHologram hologram : viewedMedia) {
                if (dynamicOnly && hologram.getType() != MediaType.VIDEO) {
                    continue;
                }
                if (!canView(player, hologram)) {
                    handleRangeExit(player, hologram);
                }
            }
        }

        List<MediaHologram> worldMedia = mediaByWorldSnapshot.get(player.getWorld().getName());
        if (worldMedia == null || worldMedia.isEmpty()) {
            return;
        }

        for (MediaHologram hologram : worldMedia) {
            if (dynamicOnly && hologram.getType() != MediaType.VIDEO) {
                continue;
            }
            if (hologram.isViewing(player) || !canView(player, hologram)) {
                continue;
            }
            handleRangeEnter(player, hologram);
        }
    }

    public void removeViewer(Player player) {
        if (player == null) {
            return;
        }

        Set<MediaHologram> viewedMedia = mediaByViewer.remove(player.getUniqueId());
        if (viewedMedia == null || viewedMedia.isEmpty()) {
            return;
        }
        for (MediaHologram hologram : viewedMedia) {
            if (!hologram.removeViewer(player)) {
                continue;
            }
            imageRenderer.hide(hologram, player);
            handleNoViewers(hologram, Bukkit.getCurrentTick());
        }
        stopUpdateTaskIfIdle();
    }

    private void handleRangeEnter(Player player, MediaHologram hologram) {
        if (!hologram.addViewer(player)) {
            return;
        }

        cancelUnloadTask(hologram);
        mediaByViewer.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(hologram);
        if (hologram.getState() != MediaState.READY) {
            return;
        }

        if (hologram.getType() == MediaType.VIDEO) {
            startVideoIfAutoplay(hologram);
            videoRenderer.renderCurrentFrame(hologram);
        }
        imageRenderer.show(hologram, player);
    }

    private void handleRangeExit(Player player, MediaHologram hologram) {
        if (!hologram.removeViewer(player)) {
            return;
        }

        removeFromViewerIndex(player.getUniqueId(), hologram);
        imageRenderer.hide(hologram, player);
        handleNoViewers(hologram, Bukkit.getCurrentTick());
        stopUpdateTaskIfIdle();
    }

    private CompletableFuture<MediaHologram> prepareMedia(MediaHologram hologram) {
        hologram.setState(MediaState.LOADING, "Preparing media asynchronously.");
        CompletableFuture<MediaHologram> future = CompletableFuture
                .supplyAsync(() -> downloadAndProcess(hologram), executor)
                .whenComplete((media, throwable) -> {
                    if (mediaHolograms.get(hologram.getId()) != hologram) {
                        return;
                    }
                    if (throwable != null) {
                        hologram.setState(MediaState.FAILED, rootCauseMessage(throwable));
                        plugin.getLogger().warning("Failed to prepare media hologram '" + hologram.getId() + "': " + hologram.getStatusMessage());
                        return;
                    }

                    hologram.setState(MediaState.READY, "Ready.");
                    if (hologram.getType() == MediaType.VIDEO && shouldAutoplay(hologram)) {
                        hologram.setPlaybackState(VideoPlaybackState.PLAYING);
                    }
                    saveMediaHologram(hologram);

                    plugin.getSchedulerUtil().runGlobal(() -> {
                        refreshOnlineViewers();
                        if (hologram.hasViewers() && hologram.getMapTiles().isEmpty()) {
                            renderCurrentMedia(hologram);
                        }
                        startUpdateTask();
                    });
                });
        return future;
    }

    private MediaHologram downloadAndProcess(MediaHologram hologram) {
        try {
            MediaDownloadResult download = downloader.download(hologram.getType(), hologram.getUrl());
            hologram.setDownloadResult(download);
            ProcessedMedia processed = frameProcessor.process(hologram.getType(), download, hologram.getSettings(), resolveMaxFrames());
            hologram.setProcessedMedia(processed);
            return hologram;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void renderCurrentMedia(MediaHologram hologram) {
        if (hologram == null || hologram.getState() != MediaState.READY || hologram.getProcessedMedia() == null) {
            return;
        }
        if (hologram.getType() == MediaType.IMAGE) {
            imageRenderer.renderFrame(hologram, hologram.getProcessedMedia().firstMapFrame());
            return;
        }
        videoRenderer.renderCurrentFrame(hologram);
    }

    private void startUpdateTask() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            stopUpdateTask();
            return;
        }
        if (!hasPeriodicMediaWork()) {
            stopUpdateTaskIfIdle();
            return;
        }
        long interval = runtimeConfig.updateIntervalTicks();
        if (updateTask != null) {
            if (updateTaskInterval == interval) {
                return;
            }
            stopUpdateTask();
        }
        updateTask = plugin.getSchedulerUtil().runGlobalAtFixedRate(() -> tick(Bukkit.getCurrentTick()), interval, interval);
        updateTaskInterval = interval;
    }

    private void tick(long currentTick) {
        if (!hasPeriodicMediaWork()) {
            stopUpdateTask();
            return;
        }

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            for (MediaHologram hologram : videoSnapshot) {
                if (hologram.hasViewers()) {
                    clearViewers(hologram);
                }
                handleNoViewers(hologram, currentTick);
            }
            stopUpdateTaskIfIdle();
            return;
        }

        if (currentTick - lastVisibilityRefreshTick >= runtimeConfig.visibilityCheckIntervalTicks()) {
            lastVisibilityRefreshTick = currentTick;
            refreshOnlineViewers(true);
        }
        int activeVideos = 0;
        int maxActiveVideos = runtimeConfig.maxActiveVideos();
        for (MediaHologram hologram : videoSnapshot) {
            if (hologram.getState() != MediaState.READY) {
                continue;
            }
            if (!hologram.hasViewers()) {
                handleNoViewers(hologram, currentTick);
                continue;
            }
            if (hologram.getType() != MediaType.VIDEO) {
                continue;
            }

            if (hologram.isAutoPaused() && hologram.getPlaybackState() == VideoPlaybackState.PLAYING) {
                hologram.setAutoPaused(false);
                hologram.setLastFrameTick(0L);
            }
            if (hologram.getPlaybackState() == VideoPlaybackState.PLAYING) {
                if (activeVideos >= maxActiveVideos) {
                    continue;
                }
                activeVideos++;
                videoRenderer.tick(hologram, currentTick, renderIntervalTicks(hologram), runtimeConfig.viewerBatchSize());
            }
        }
        stopUpdateTaskIfIdle();
    }

    private void handleNoViewers(MediaHologram hologram, long currentTick) {
        if (hologram.hasViewers()) {
            unloadIfUnused(hologram, currentTick);
            return;
        }
        if (hologram.getType() == MediaType.VIDEO
                && hologram.getPlaybackState() == VideoPlaybackState.PLAYING
                && runtimeConfig.autoPauseWithoutViewers()) {
            hologram.setAutoPaused(true);
            hologram.setLastFrameTick(0L);
        }
        unloadIfUnused(hologram, currentTick);
    }

    private void unloadIfUnused(MediaHologram hologram, long currentTick) {
        if (hologram.hasViewers()) {
            cancelUnloadTask(hologram);
            hologram.clearEmptySince();
            return;
        }

        if (!runtimeConfig.unloadWhenEmpty()) {
            return;
        }

        long emptySinceTick = hologram.markEmptySince(currentTick);
        long remainingDelay = runtimeConfig.unloadDelayTicks() - Math.max(0L, currentTick - emptySinceTick);
        if (remainingDelay <= 0L) {
            cancelUnloadTask(hologram);
            imageRenderer.destroy(hologram);
            return;
        }

        unloadTasks.computeIfAbsent(hologram, ignored -> plugin.getSchedulerUtil().runGlobalDelayed(task -> {
            unloadTasks.remove(hologram);
            unloadIfUnused(hologram, Bukkit.getCurrentTick());
        }, remainingDelay));
    }

    private void startVideoIfAutoplay(MediaHologram hologram) {
        if (hologram == null
                || hologram.getType() != MediaType.VIDEO
                || hologram.getState() != MediaState.READY
                || !shouldAutoplay(hologram)) {
            return;
        }

        if (hologram.isAutoPaused()) {
            hologram.setAutoPaused(false);
            hologram.setLastFrameTick(0L);
        }
        if (hologram.getPlaybackState() != VideoPlaybackState.PLAYING) {
            hologram.setPlaybackState(VideoPlaybackState.PLAYING);
            hologram.setLastFrameTick(0L);
        }
        startUpdateTask();
    }

    private int renderIntervalTicks(MediaHologram hologram) {
        if (!runtimeConfig.adaptiveFrameThrottle()) {
            return 1;
        }
        ProcessedMedia processedMedia = hologram.getProcessedMedia();
        MapFrameData frame = processedMedia == null ? null : processedMedia.mapFrame(hologram.getCurrentFrame());
        if (frame == null) {
            return 1;
        }
        int frameCost = frame.tileCount()
                * Math.max(1, hologram.viewerCount())
                * Math.max(1, hologram.getSettings().fps());
        int maxCost = runtimeConfig.maxFrameCostPerTick();
        if (frameCost <= maxCost) {
            return 1;
        }
        int interval = (int) Math.ceil((double) frameCost / maxCost);
        return Math.max(1, Math.min(runtimeConfig.maxVisualSkipTicks(), interval));
    }

    private boolean shouldAutoplay(MediaHologram hologram) {
        return hologram != null
                && hologram.getType() == MediaType.VIDEO
                && (hologram.getSettings().autoplay() || runtimeConfig.videoAutoplay());
    }

    private boolean canView(Player player, MediaHologram hologram) {
        if (!hologram.canView(player)) {
            return false;
        }
        if (!runtimeConfig.lineOfSightEnabled()) {
            return true;
        }
        return isLookingAtMedia(player, hologram);
    }

    private boolean isLookingAtMedia(Player player, MediaHologram hologram) {
        Location eyeLocation = player.getEyeLocation();
        Location target = hologram.getResolvedLocationView();
        if (target == null || target.getWorld() == null || eyeLocation.getWorld() != target.getWorld()) {
            return false;
        }

        Vector toTarget = target.toVector().subtract(eyeLocation.toVector());
        double distance = toTarget.length();
        if (distance <= 0.1D) {
            return true;
        }
        Vector direction = toTarget.multiply(1.0D / distance);
        if (eyeLocation.getDirection().dot(direction) < runtimeConfig.lineOfSightMinDot()) {
            return false;
        }
        if (!runtimeConfig.lineOfSightBlockOcclusion()) {
            return true;
        }

        RayTraceResult result = player.getWorld().rayTraceBlocks(eyeLocation, direction, distance, FluidCollisionMode.NEVER, true);
        return result == null;
    }

    private void stopUpdateTaskIfIdle() {
        if (!hasPeriodicMediaWork()) {
            stopUpdateTask();
        }
    }

    private boolean hasPeriodicMediaWork() {
        for (MediaHologram hologram : videoSnapshot) {
            ProcessedMedia processedMedia = hologram.getProcessedMedia();
            if (hologram.getState() == MediaState.READY
                    && processedMedia != null
                    && processedMedia.frameCount() > 1
                    && hologram.hasViewers()
                    && hologram.getPlaybackState() == VideoPlaybackState.PLAYING
                    && !hologram.isAutoPaused()) {
                return true;
            }
        }
        return false;
    }

    private void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        updateTaskInterval = 0L;
    }

    private void cancelUnloadTask(MediaHologram hologram) {
        SchedulerUtil.TaskHandle task = unloadTasks.remove(hologram);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelUnloadTasks() {
        for (SchedulerUtil.TaskHandle task : unloadTasks.values()) {
            task.cancel();
        }
        unloadTasks.clear();
    }

    private void clearViewers(MediaHologram hologram) {
        if (hologram == null) {
            return;
        }
        for (UUID viewerId : hologram.viewers()) {
            removeFromViewerIndex(viewerId, hologram);
        }
        hologram.clearViewers();
    }

    private void removeFromViewerIndex(UUID viewerId, MediaHologram hologram) {
        mediaByViewer.computeIfPresent(viewerId, (ignored, holograms) -> {
            holograms.remove(hologram);
            return holograms.isEmpty() ? null : holograms;
        });
    }

    private void reloadRuntimeConfig() {
        runtimeConfig = MediaRuntimeConfig.from(plugin.getConfigManager().getMedia());
    }

    private void rebuildSnapshots() {
        List<MediaHologram> media = List.copyOf(mediaHolograms.values());
        List<MediaHologram> videos = new ArrayList<>();
        Map<String, List<MediaHologram>> byWorld = new HashMap<>();
        for (MediaHologram hologram : media) {
            if (hologram.getType() == MediaType.VIDEO) {
                videos.add(hologram);
            }
            String worldName = hologram.getWorldName();
            if (worldName != null && !worldName.isBlank()) {
                byWorld.computeIfAbsent(worldName, ignored -> new ArrayList<>()).add(hologram);
            }
        }
        Map<String, List<MediaHologram>> immutableByWorld = new HashMap<>();
        for (Map.Entry<String, List<MediaHologram>> entry : byWorld.entrySet()) {
            immutableByWorld.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        mediaSnapshot = media;
        videoSnapshot = List.copyOf(videos);
        mediaByWorldSnapshot = Map.copyOf(immutableByWorld);
    }

    private void destroyRuntime() {
        cancelUnloadTasks();
        for (MediaHologram hologram : mediaSnapshot) {
            imageRenderer.destroy(hologram);
            clearViewers(hologram);
        }
        mediaByViewer.clear();
    }

    private boolean canAddToWorld(String worldName) {
        int maxPerWorld = runtimeConfig.maxMediaPerWorld();
        if (maxPerWorld <= 0) {
            return true;
        }
        int count = 0;
        for (MediaHologram media : mediaHolograms.values()) {
            if (Objects.equals(media.getWorldName(), worldName)) {
                count++;
            }
        }
        return count < maxPerWorld;
    }

    private boolean isSystemEnabled() {
        return runtimeConfig.systemEnabled();
    }

    private boolean isTypeEnabled(MediaType type) {
        return type == MediaType.VIDEO ? runtimeConfig.videosEnabled() : runtimeConfig.imagesEnabled();
    }

    private int resolveMaxFrames() {
        return runtimeConfig.maxFrames();
    }

    public static boolean isMediaConfiguration(ConfigurationSection section) {
        if (section == null) {
            return false;
        }
        MediaType type = MediaType.fromString(section.getString("type"));
        return type != null && type.isMedia();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    private static final class MediaThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "AxoHologram-Media-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
