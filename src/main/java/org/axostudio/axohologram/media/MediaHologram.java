package org.axostudio.axohologram.media;

import org.axostudio.axohologram.AxoHologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MediaHologram {

    private final AxoHologram plugin;
    private final String id;
    private final MediaType type;
    private final URI url;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final Set<Player> viewerPlayers = ConcurrentHashMap.newKeySet();
    private final List<MediaMapTile> changedTilesBuffer = new ArrayList<>();
    private final Map<UUID, Long> shownDisplayGenerations = new ConcurrentHashMap<>();
    private final AtomicLong displayGeneration = new AtomicLong();

    private volatile boolean enabled = true;
    private volatile String worldName;
    private volatile Location location;
    private volatile MediaSettings settings;
    private volatile MediaState state = MediaState.UNLOADED;
    private volatile String statusMessage = "";
    private volatile MediaDownloadResult downloadResult;
    private volatile ProcessedMedia processedMedia;
    private volatile VideoPlaybackState playbackState = VideoPlaybackState.STOPPED;
    private volatile boolean autoPaused;
    private volatile int currentFrame;
    private volatile long lastFrameTick;
    private volatile long lastFrameNanos;
    private volatile int lastRenderedFrame = -1;
    private volatile long lastRenderTick;
    private volatile long emptySinceTick;
    private volatile int viewerBatchCursor;

    private volatile List<MediaMapTile> mapTiles = List.of();

    public MediaHologram(AxoHologram plugin, String id, MediaType type, URI url, Location location, MediaSettings settings) {
        this.plugin = plugin;
        this.id = id;
        this.type = type;
        this.url = url;
        setLocation(location);
        this.settings = settings;
        if (type == MediaType.IMAGE) {
            this.playbackState = VideoPlaybackState.PLAYING;
        }
    }

    public String getId() {
        return id;
    }

    public MediaType getType() {
        return type;
    }

    public URI getUrl() {
        return url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getLocation() {
        resolveWorldIfNeeded();
        return location.clone();
    }

    public Location getResolvedLocationView() {
        resolveWorldIfNeeded();
        return location;
    }

    public void setLocation(Location location) {
        Location cloned = location == null ? new Location(null, 0.0D, 0.0D, 0.0D) : location.clone();
        this.location = cloned;
        if (cloned.getWorld() != null) {
            this.worldName = cloned.getWorld().getName();
        }
    }

    public MediaSettings getSettings() {
        return settings;
    }

    public void setSettings(MediaSettings settings) {
        this.settings = settings;
    }

    public MediaState getState() {
        return state;
    }

    public void setState(MediaState state, String statusMessage) {
        this.state = state == null ? MediaState.UNLOADED : state;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public MediaDownloadResult getDownloadResult() {
        return downloadResult;
    }

    public void setDownloadResult(MediaDownloadResult downloadResult) {
        this.downloadResult = downloadResult;
    }

    public ProcessedMedia getProcessedMedia() {
        return processedMedia;
    }

    public void setProcessedMedia(ProcessedMedia processedMedia) {
        this.processedMedia = processedMedia;
        this.currentFrame = 0;
        this.lastFrameTick = 0L;
        this.lastFrameNanos = 0L;
        this.lastRenderedFrame = -1;
        this.lastRenderTick = 0L;
    }

    public VideoPlaybackState getPlaybackState() {
        return playbackState;
    }

    public void setPlaybackState(VideoPlaybackState playbackState) {
        this.playbackState = playbackState == null ? VideoPlaybackState.STOPPED : playbackState;
        if (this.playbackState == VideoPlaybackState.STOPPED) {
            currentFrame = 0;
            lastFrameNanos = 0L;
            lastRenderedFrame = -1;
            lastRenderTick = 0L;
        }
        autoPaused = false;
    }

    public boolean isAutoPaused() {
        return autoPaused;
    }

    public void setAutoPaused(boolean autoPaused) {
        this.autoPaused = autoPaused;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public void setCurrentFrame(int currentFrame) {
        this.currentFrame = Math.max(0, currentFrame);
    }

    public long getLastFrameTick() {
        return lastFrameTick;
    }

    public void setLastFrameTick(long lastFrameTick) {
        this.lastFrameTick = lastFrameTick;
        if (lastFrameTick <= 0L) {
            this.lastFrameNanos = 0L;
        }
    }

    public long getLastFrameNanos() {
        return lastFrameNanos;
    }

    public void setLastFrameNanos(long lastFrameNanos) {
        this.lastFrameNanos = Math.max(0L, lastFrameNanos);
    }

    public int viewerCount() {
        return viewers.size();
    }

    public boolean hasViewers() {
        return !viewers.isEmpty();
    }

    public Set<UUID> viewers() {
        return Set.copyOf(viewers);
    }

    public Set<Player> viewerPlayers() {
        return viewerPlayers;
    }

    public List<MediaMapTile> changedTilesBuffer() {
        return changedTilesBuffer;
    }

    public boolean addViewer(Player player) {
        if (player == null) {
            return false;
        }
        boolean added = viewers.add(player.getUniqueId());
        if (player.isOnline()) {
            viewerPlayers.add(player);
        }
        if (added) {
            emptySinceTick = 0L;
        }
        return added;
    }

    public boolean removeViewer(Player player) {
        if (player == null) {
            return false;
        }
        viewerPlayers.remove(player);
        return viewers.remove(player.getUniqueId());
    }

    public boolean isViewing(Player player) {
        return player != null && viewers.contains(player.getUniqueId());
    }

    public void clearViewers() {
        viewers.clear();
        viewerPlayers.clear();
        shownDisplayGenerations.clear();
    }

    public boolean hasCurrentDisplays(Player player) {
        if (player == null) {
            return false;
        }
        Long shownGeneration = shownDisplayGenerations.get(player.getUniqueId());
        return shownGeneration != null && shownGeneration == displayGeneration.get();
    }

    public boolean markDisplaysShown(Player player) {
        if (player == null) {
            return false;
        }
        long currentGeneration = displayGeneration.get();
        Long previousGeneration = shownDisplayGenerations.put(player.getUniqueId(), currentGeneration);
        return previousGeneration == null || previousGeneration != currentGeneration;
    }

    public void markDisplaysHidden(Player player) {
        if (player != null) {
            shownDisplayGenerations.remove(player.getUniqueId());
        }
    }

    public void markDisplaysChanged() {
        displayGeneration.incrementAndGet();
        shownDisplayGenerations.clear();
    }

    public void clearDisplayedViewers() {
        shownDisplayGenerations.clear();
    }

    public long markEmptySince(long currentTick) {
        if (emptySinceTick <= 0L) {
            emptySinceTick = Math.max(1L, currentTick);
        }
        return emptySinceTick;
    }

    public void clearEmptySince() {
        emptySinceTick = 0L;
    }

    public int getLastRenderedFrame() {
        return lastRenderedFrame;
    }

    public void setLastRenderedFrame(int lastRenderedFrame) {
        this.lastRenderedFrame = lastRenderedFrame;
    }

    public long getLastRenderTick() {
        return lastRenderTick;
    }

    public void setLastRenderTick(long lastRenderTick) {
        this.lastRenderTick = lastRenderTick;
    }

    public int nextViewerBatchStart(int viewerCount, int batchSize) {
        if (viewerCount <= 0 || batchSize <= 0 || batchSize >= viewerCount) {
            viewerBatchCursor = 0;
            return 0;
        }
        int start = Math.floorMod(viewerBatchCursor, viewerCount);
        viewerBatchCursor = (start + batchSize) % viewerCount;
        return start;
    }

    public boolean canView(Player player) {
        if (player == null || !player.isOnline() || !enabled) {
            return false;
        }
        Location currentLocation = getResolvedLocationView();
        if (currentLocation == null || currentLocation.getWorld() == null) {
            return false;
        }
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        if (!player.getWorld().getName().equals(worldName)) {
            return false;
        }
        int distance = settings == null ? 32 : settings.renderDistance();
        return currentLocation.distanceSquared(player.getLocation()) <= (double) distance * distance;
    }

    public List<MediaMapTile> getMapTiles() {
        return mapTiles;
    }

    public void setMapTiles(List<MediaMapTile> mapTiles) {
        this.mapTiles = mapTiles == null ? List.of() : List.copyOf(mapTiles);
    }

    public void serialize(ConfigurationSection section) {
        section.set("id", id);
        section.set("type", type.name());
        section.set("enabled", enabled ? null : false);
        section.set("url", url.toString());
        section.set("location.world", worldName);
        section.set("location.x", location.getX());
        section.set("location.y", location.getY());
        section.set("location.z", location.getZ());
        section.set("location.yaw", location.getYaw());
        section.set("location.pitch", location.getPitch());
        section.set("visibility.distance", settings == null ? null : settings.renderDistance());
        ConfigurationSection settingsSection = section.createSection("settings");
        settings.serialize(settingsSection, type);
    }

    public static MediaHologram deserialize(String id, ConfigurationSection section, AxoHologram plugin) {
        MediaType type = MediaType.fromString(section.getString("type"));
        if (type == null || !type.isMedia()) {
            return null;
        }

        String rawUrl = section.getString("url");
        if (rawUrl == null || rawUrl.isBlank()) {
            plugin.getLogger().warning("Media hologram '" + id + "' has no url.");
            return null;
        }

        String worldName = section.getString("location.world");
        if (worldName == null || worldName.isBlank()) {
            plugin.getLogger().warning("Media hologram '" + id + "' has no world.");
            return null;
        }

        try {
            World world = Bukkit.getWorld(worldName);
            Location location = new Location(
                    world,
                    section.getDouble("location.x"),
                    section.getDouble("location.y"),
                    section.getDouble("location.z"),
                    (float) section.getDouble("location.yaw", 0.0D),
                    (float) section.getDouble("location.pitch", 0.0D)
            );
            MediaSettings settings = MediaSettings.fromSection(type, section.getConfigurationSection("settings"), plugin.getConfigManager().getMedia());
            if (section.contains("visibility.distance")) {
                settings = settings.withRenderDistance(section.getInt("visibility.distance", settings.renderDistance()));
            }
            MediaHologram hologram = new MediaHologram(plugin, id, type, new URI(rawUrl), location, settings);
            hologram.worldName = worldName;
            hologram.enabled = section.getBoolean("enabled", true);
            if (type == MediaType.VIDEO && settings.autoplay()) {
                hologram.playbackState = VideoPlaybackState.PLAYING;
            }
            return hologram;
        } catch (URISyntaxException exception) {
            plugin.getLogger().warning("Media hologram '" + id + "' has invalid URL: " + rawUrl);
            return null;
        }
    }

    public YamlConfiguration toYaml() {
        YamlConfiguration configuration = new YamlConfiguration();
        serialize(configuration);
        return configuration;
    }

    private void resolveWorldIfNeeded() {
        if (location.getWorld() != null || worldName == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            location.setWorld(world);
        }
    }
}
