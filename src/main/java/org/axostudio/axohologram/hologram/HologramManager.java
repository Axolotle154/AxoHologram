package org.axostudio.axohologram.hologram;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.factory.HologramFactory;
import org.axostudio.axohologram.hologram.impl.AxoHologramImpl;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.hologram.page.impl.AxoHologramPageImpl;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class HologramManager {

    public static final Pattern VALID_HOLOGRAM_ID = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final AxoHologram plugin;
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();
    private final Map<String, SchedulerUtil.TaskHandle> temporaryRemovalTasks = new ConcurrentHashMap<>();
    private final Multimap<UUID, Hologram> activeHolograms = Multimaps.synchronizedSetMultimap(HashMultimap.create());
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
        plugin.getLogger().info("Loading holograms...");

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

    public void destroyAllHolograms() {
        holograms.values().forEach(Hologram::destroy);
    }

    public void reload() {
        destroyAllHolograms();
        loadHolograms();
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Hologram hologram : holograms.values()) {
                hologram.updateVisibility(player, true);
            }
        }
    }

    public void shutdown() {
        stopTasks();
        cancelTemporaryRemovalTasks();
        saveHolograms();
        destroyAllHolograms();
    }

    public void setTextLines(Hologram hologram, List<String> lines) {
        if (hologram == null || lines == null) {
            return;
        }

        HologramPage page = hologram.getPage(0);
        if (page == null) {
            page = new AxoHologramPageImpl();
            hologram.addPage(page);
        }

        while (!page.getLines().isEmpty()) {
            page.removeLine(0);
        }
        for (String line : lines) {
            page.addLine(new TextLineImpl(line == null ? "" : line, plugin));
        }

        saveHologram(hologram);
        hologram.refreshViewers();
        restartRefreshTask();
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
        startRefreshTask();
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
        activeHolograms.put(player.getUniqueId(), hologram);
    }

    public void onViewerRemoved(Player player, Hologram hologram) {
        activeHolograms.remove(player.getUniqueId(), hologram);
    }

    private void startTasks() {
        startVisibilityTask();
        startRefreshTask();
    }

    private void startVisibilityTask() {
        long interval = plugin.getConfigManager().getConfig().getLong("general.visibility-check-interval", 5L);
        if (interval <= 0L) {
            return;
        }

        visibilityTask = plugin.getSchedulerUtil().runGlobalAtFixedRate(task -> {
            if (holograms.isEmpty()) {
                return;
            }

            for (Hologram hologram : holograms.values()) {
                World world = hologram.getLocation().getWorld();
                if (world == null) {
                    continue;
                }

                int viewDistance = hologram.getViewDistance() > 0
                        ? hologram.getViewDistance()
                        : plugin.getConfigManager().getConfig().getInt("general.view-distance", 48);

                for (Player player : world.getNearbyPlayers(hologram.getLocation(), viewDistance)) {
                    hologram.updateVisibility(player, false);
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                List<Hologram> trackedHolograms;
                synchronized (activeHolograms) {
                    trackedHolograms = List.copyOf(activeHolograms.get(player.getUniqueId()));
                }
                for (Hologram hologram : trackedHolograms) {
                    hologram.updateVisibility(player, false);
                }
            }
        }, interval, interval);
    }

    private void startRefreshTask() {
        long interval = resolveRefreshSchedulerInterval();
        if (interval <= 0L) {
            return;
        }

        refreshTask = plugin.getSchedulerUtil().runGlobalAtFixedRate(task -> {
            if (holograms.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }

            long currentTick = Bukkit.getCurrentTick();
            for (Hologram hologram : holograms.values()) {
                if (!hologram.requiresPeriodicRefresh()) {
                    continue;
                }
                if (hologram instanceof AxoHologramImpl axoHologram && !axoHologram.shouldPeriodicRefresh(currentTick)) {
                    continue;
                }
                hologram.refreshViewers();
            }
        }, interval, interval);
    }

    private long resolveRefreshSchedulerInterval() {
        long interval = plugin.getConfigManager().getConfig().getLong("placeholders.refresh-interval", 20L);
        if (interval <= 0L) {
            interval = Long.MAX_VALUE;
        }

        for (Hologram hologram : holograms.values()) {
            long hologramInterval = hologram.getUpdateTextInterval();
            if (hologramInterval > 0L) {
                interval = Math.min(interval, hologramInterval);
            }
        }

        return interval == Long.MAX_VALUE ? -1L : interval;
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
    }
}
