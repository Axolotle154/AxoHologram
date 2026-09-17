package org.axostudio.axohologram.platform.packet;

import net.kyori.adventure.text.Component;
import org.axostudio.axohologram.api.hologram.Hologram;
import org.axostudio.axohologram.api.hologram.HologramLine;
import org.axostudio.axohologram.platform.entity.BlockDisplayAdapter;
import org.axostudio.axohologram.platform.entity.DisplayEntityFactory;
import org.axostudio.axohologram.platform.entity.ItemDisplayAdapter;
import org.axostudio.axohologram.platform.entity.TextDisplayAdapter;
import org.axostudio.axohologram.platform.scheduler.AxoScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramPacketManager {

    private static Plugin pluginInstance;
    private static AxoScheduler schedulerInstance;
    private static final PacketViewerTracker TRACKER = new PacketViewerTracker();

    public static void initialize(Plugin plugin, AxoScheduler scheduler) {
        pluginInstance = plugin;
        schedulerInstance = scheduler;
    }

    public static void init(Plugin plugin, AxoScheduler.TaskHandle handle) {
        pluginInstance = plugin;
    }

    public static boolean isTrackedInteraction(UUID entityId) {
        return TRACKER.isTracked(entityId);
    }

    public static String getHologramIdForEntity(UUID entityId) {
        PacketViewerTracker.TrackedDisplay display = TRACKER.getTrackedDisplay(entityId);
        return display != null ? display.hologramId() : null;
    }

    public static PacketViewerTracker tracker() {
        return TRACKER;
    }

    public static PacketViewerTracker.TrackedDisplay getTrackedDisplay(UUID entityId) {
        return TRACKER.getTrackedDisplay(entityId);
    }

    public static boolean isTrackedDisplayEntity(UUID entityId) {
        return TRACKER.isTracked(entityId);
    }

    public static boolean isTrackedDisplayEntity(Entity entity) {
        return entity != null && isTrackedDisplayEntity(entity.getUniqueId());
    }

    public static void spawnTextLine(
            Player player,
            Hologram hologram,
            int pageIndex,
            int lineIndex,
            Location location,
            Component text,
            Display.Billboard billboard,
            HologramLine line
    ) {
        if (player == null || location == null || location.getWorld() == null) return;
        UUID viewerId = player.getUniqueId();
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey(hologram.getId(), pageIndex, lineIndex);

        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);

        Location spawnLoc = location.clone();
        getScheduler().runAtLocation(spawnLoc, () -> {
            if (spawnLoc.getWorld() == null) return;
            TextDisplay display = DisplayPacketFactory.spawnTextDisplay(
                    spawnLoc,
                    billboard != null ? billboard : hologram.getBillboard(),
                    hologram.getViewDistance() > 0 ? hologram.getViewDistance() : 48f,
                    hologram.getShadowRadius(),
                    hologram.getShadowStrength(),
                    text,
                    hologram.getBackgroundColor(),
                    hologram.hasTextShadow(),
                    hologram.isSeeThrough(),
                    hologram.getAlignment(),
                    resolveTextLineWidth(),
                    hologram.getScaleX() * lineScaleX(line) * resolveTextHorizontalScale(),
                    hologram.getScaleY() * lineScaleY(line),
                    hologram.getScaleZ() * lineScaleZ(line),
                    0
            );

            if (display == null) return;
            TRACKER.trackDisplay(display.getUniqueId(), viewerId, hologram.getId(), pageIndex, lineIndex);
            TRACKER.getPlayerEntities(viewerId).put(key, display.getUniqueId());

            if (pluginInstance != null) {
                player.showEntity(pluginInstance, display);
            }

            spawnInteractionIfNeeded(player, hologram, spawnLoc, viewerId, key);
        });
    }

    public static void updateTextLine(
            Player player,
            Hologram hologram,
            int pageIndex,
            int lineIndex,
            Location location,
            Component text,
            Display.Billboard billboard,
            HologramLine line
    ) {
        if (player == null) return;
        UUID viewerId = player.getUniqueId();
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = TRACKER.getPlayerEntities(viewerId).get(key);
        if (entityId == null) {
            spawnTextLine(player, hologram, pageIndex, lineIndex, location, text, billboard, line);
            return;
        }

        Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof TextDisplay display && display.isValid()) {
            getScheduler().runAtEntity(display, () -> {
                if (text != null) display.text(text);
                display.setBillboard(billboard != null ? billboard : hologram.getBillboard());
                display.setViewRange(hologram.getViewDistance() > 0 ? hologram.getViewDistance() : 48f);
                display.setShadowRadius(hologram.getShadowRadius());
                display.setShadowStrength(hologram.getShadowStrength());
                TextDisplayAdapter.applyTextStyle(
                        display,
                        text,
                        hologram.getBackgroundColor(),
                        hologram.hasTextShadow(),
                        hologram.isSeeThrough(),
                        hologram.getAlignment(),
                        resolveTextLineWidth()
                );
                TextDisplayAdapter.applyTransformation(
                        display,
                        hologram.getScaleX() * lineScaleX(line) * resolveTextHorizontalScale(),
                        hologram.getScaleY() * lineScaleY(line),
                        hologram.getScaleZ() * lineScaleZ(line),
                        0
                );
                if (location != null && shouldTeleport(display.getLocation(), location)) {
                    display.teleportAsync(location);
                }
            });
        } else {
            spawnTextLine(player, hologram, pageIndex, lineIndex, location, text, billboard, line);
        }
    }

    public static void spawnItemLine(
            Player player,
            Hologram hologram,
            int pageIndex,
            int lineIndex,
            Location location,
            ItemStack itemStack,
            Display.Billboard billboard,
            HologramLine line
    ) {
        if (player == null || location == null || location.getWorld() == null) return;
        UUID viewerId = player.getUniqueId();
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey(hologram.getId(), pageIndex, lineIndex);

        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);

        Location spawnLoc = location.clone();
        getScheduler().runAtLocation(spawnLoc, () -> {
            if (spawnLoc.getWorld() == null) return;
            ItemDisplay display = DisplayPacketFactory.spawnItemDisplay(
                    spawnLoc,
                    billboard != null ? billboard : hologram.getBillboard(),
                    hologram.getViewDistance() > 0 ? hologram.getViewDistance() : 48f,
                    hologram.getShadowRadius(),
                    hologram.getShadowStrength(),
                    itemStack,
                    ItemDisplay.ItemDisplayTransform.FIXED,
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(
                            hologram.getScaleX() * lineScaleX(line),
                            hologram.getScaleY() * lineScaleY(line),
                            hologram.getScaleZ() * lineScaleZ(line)
                    ),
                    new Quaternionf(),
                    0
            );

            if (display == null) return;
            TRACKER.trackDisplay(display.getUniqueId(), viewerId, hologram.getId(), pageIndex, lineIndex);
            TRACKER.getPlayerEntities(viewerId).put(key, display.getUniqueId());

            if (pluginInstance != null) {
                player.showEntity(pluginInstance, display);
            }
            spawnInteractionIfNeeded(player, hologram, spawnLoc, viewerId, key);
        });
    }

    public static void updateItemLine(
            Player player,
            Hologram hologram,
            int pageIndex,
            int lineIndex,
            Location location,
            ItemStack itemStack,
            Display.Billboard billboard,
            HologramLine line
    ) {
        if (player == null) return;
        UUID viewerId = player.getUniqueId();
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = TRACKER.getPlayerEntities(viewerId).get(key);
        if (entityId == null) {
            spawnItemLine(player, hologram, pageIndex, lineIndex, location, itemStack, billboard, line);
            return;
        }

        Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof ItemDisplay display && display.isValid()) {
            getScheduler().runAtEntity(display, () -> {
                if (itemStack != null) display.setItemStack(itemStack);
                if (location != null && shouldTeleport(display.getLocation(), location)) {
                    display.teleportAsync(location);
                }
            });
        } else {
            spawnItemLine(player, hologram, pageIndex, lineIndex, location, itemStack, billboard, line);
        }
    }

    public static void spawnBlockLine(
            Player player,
            Hologram hologram,
            int pageIndex,
            int lineIndex,
            Location location,
            BlockData blockData,
            Display.Billboard billboard,
            HologramLine line
    ) {
        if (player == null || location == null || location.getWorld() == null) return;
        UUID viewerId = player.getUniqueId();
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey(hologram.getId(), pageIndex, lineIndex);

        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);

        Location spawnLoc = location.clone();
        getScheduler().runAtLocation(spawnLoc, () -> {
            if (spawnLoc.getWorld() == null) return;
            BlockDisplay display = DisplayPacketFactory.spawnBlockDisplay(
                    spawnLoc,
                    billboard != null ? billboard : hologram.getBillboard(),
                    hologram.getViewDistance() > 0 ? hologram.getViewDistance() : 48f,
                    hologram.getShadowRadius(),
                    hologram.getShadowStrength(),
                    blockData,
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(
                            hologram.getScaleX() * lineScaleX(line),
                            hologram.getScaleY() * lineScaleY(line),
                            hologram.getScaleZ() * lineScaleZ(line)
                    ),
                    new Quaternionf(),
                    0
            );

            if (display == null) return;
            TRACKER.trackDisplay(display.getUniqueId(), viewerId, hologram.getId(), pageIndex, lineIndex);
            TRACKER.getPlayerEntities(viewerId).put(key, display.getUniqueId());

            if (pluginInstance != null) {
                player.showEntity(pluginInstance, display);
            }
            spawnInteractionIfNeeded(player, hologram, spawnLoc, viewerId, key);
        });
    }

    public static void updateBlockLine(
            Player player,
            Hologram hologram,
            int pageIndex,
            int lineIndex,
            Location location,
            BlockData blockData,
            Display.Billboard billboard,
            HologramLine line
    ) {
        if (player == null) return;
        UUID viewerId = player.getUniqueId();
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = TRACKER.getPlayerEntities(viewerId).get(key);
        if (entityId == null) {
            spawnBlockLine(player, hologram, pageIndex, lineIndex, location, blockData, billboard, line);
            return;
        }

        Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof BlockDisplay display && display.isValid()) {
            getScheduler().runAtEntity(display, () -> {
                if (blockData != null) display.setBlock(blockData);
                if (location != null && shouldTeleport(display.getLocation(), location)) {
                    display.teleportAsync(location);
                }
            });
        } else {
            spawnBlockLine(player, hologram, pageIndex, lineIndex, location, blockData, billboard, line);
        }
    }

    public static void destroyLine(UUID viewerId, String hologramId, int pageIndex, int lineIndex) {
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey(hologramId, pageIndex, lineIndex);
        UUID displayId = TRACKER.getPlayerEntities(viewerId).remove(key);
        if (displayId != null) {
            removeEntity(displayId);
            TRACKER.untrack(displayId);
        }
        UUID interactionId = TRACKER.getPlayerInteractions(viewerId).remove(key);
        if (interactionId != null) {
            removeEntity(interactionId);
            TRACKER.untrack(interactionId);
        }
    }

    public static void destroyLine(Player player, String hologramId, int pageIndex, int lineIndex) {
        if (player != null) {
            destroyLine(player.getUniqueId(), hologramId, pageIndex, lineIndex);
        }
    }

    public static void destroyHologram(Player player, String hologramId) {
        if (player != null) {
            destroyHologram(player.getUniqueId(), hologramId);
        }
    }

    public static void destroyHologram(UUID viewerId, String hologramId) {
        Map<PacketViewerTracker.LineKey, UUID> entities = TRACKER.getPlayerEntities(viewerId);
        for (PacketViewerTracker.LineKey key : new HashSet<>(entities.keySet())) {
            if (key.hologramId().equals(hologramId)) {
                UUID id = entities.remove(key);
                if (id != null) {
                    removeEntity(id);
                    TRACKER.untrack(id);
                }
            }
        }
        Map<PacketViewerTracker.LineKey, UUID> interactions = TRACKER.getPlayerInteractions(viewerId);
        for (PacketViewerTracker.LineKey key : new HashSet<>(interactions.keySet())) {
            if (key.hologramId().equals(hologramId)) {
                UUID id = interactions.remove(key);
                if (id != null) {
                    removeEntity(id);
                    TRACKER.untrack(id);
                }
            }
        }
    }

    public static void destroyOtherPages(Player player, String hologramId, int visiblePageIndex) {
        if (player != null) {
            destroyOtherPages(player.getUniqueId(), hologramId, visiblePageIndex);
        }
    }

    public static void destroyOtherPages(UUID viewerId, String hologramId, int visiblePageIndex) {
        Map<PacketViewerTracker.LineKey, UUID> entities = TRACKER.getPlayerEntities(viewerId);
        for (PacketViewerTracker.LineKey key : new HashSet<>(entities.keySet())) {
            if (key.hologramId().equals(hologramId) && key.pageIndex() != visiblePageIndex) {
                UUID id = entities.remove(key);
                if (id != null) {
                    removeEntity(id);
                    TRACKER.untrack(id);
                }
            }
        }
        Map<PacketViewerTracker.LineKey, UUID> interactions = TRACKER.getPlayerInteractions(viewerId);
        for (PacketViewerTracker.LineKey key : new HashSet<>(interactions.keySet())) {
            if (key.hologramId().equals(hologramId) && key.pageIndex() != visiblePageIndex) {
                UUID id = interactions.remove(key);
                if (id != null) {
                    removeEntity(id);
                    TRACKER.untrack(id);
                }
            }
        }
    }

    public static void destroyLinesExcept(Player player, String hologramId, int pageIndex, Set<Integer> keepLines) {
        if (player != null) {
            destroyLinesExcept(player.getUniqueId(), hologramId, pageIndex, keepLines);
        }
    }

    public static void destroyLinesExcept(UUID viewerId, String hologramId, int pageIndex, Set<Integer> keepLines) {
        Map<PacketViewerTracker.LineKey, UUID> entities = TRACKER.getPlayerEntities(viewerId);
        for (PacketViewerTracker.LineKey key : new HashSet<>(entities.keySet())) {
            if (key.hologramId().equals(hologramId) && key.pageIndex() == pageIndex) {
                if (keepLines == null || !keepLines.contains(key.lineIndex())) {
                    UUID id = entities.remove(key);
                    if (id != null) {
                        removeEntity(id);
                        TRACKER.untrack(id);
                    }
                }
            }
        }
    }

    public static void destroyAllHologramLinesForPlayer(Player player) {
        if (player == null) return;
        UUID viewerId = player.getUniqueId();
        Map<PacketViewerTracker.LineKey, UUID> entities = TRACKER.getPlayerEntities(viewerId);
        for (UUID id : entities.values()) {
            removeEntity(id);
        }
        Map<PacketViewerTracker.LineKey, UUID> interactions = TRACKER.getPlayerInteractions(viewerId);
        for (UUID id : interactions.values()) {
            removeEntity(id);
        }
        TRACKER.clearPlayer(viewerId);
    }

    public static void hideAllTrackedEntitiesForPlayer(Player player) {
        if (player == null || pluginInstance == null) return;
        UUID viewerId = player.getUniqueId();
        for (UUID id : TRACKER.getPlayerEntities(viewerId).values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                player.hideEntity(pluginInstance, entity);
            }
        }
        for (UUID id : TRACKER.getPlayerInteractions(viewerId).values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                player.hideEntity(pluginInstance, entity);
            }
        }
    }

    public static void clearAll() {
        for (UUID viewerId : Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList()) {
            for (UUID id : TRACKER.getPlayerEntities(viewerId).values()) {
                removeEntity(id);
            }
            for (UUID id : TRACKER.getPlayerInteractions(viewerId).values()) {
                removeEntity(id);
            }
        }
        TRACKER.clearAll();
    }

    public static void cleanupAll() {
        clearAll();
    }

    public static void hideHologram(Player player, String hologramId) {
        destroyHologram(player, hologramId);
    }

    public static void destroyAllForHologram(String hologramId) {
        if (hologramId == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideHologram(player, hologramId);
        }
    }

    private static void removeEntity(UUID entityId) {
        if (entityId == null) return;
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid()) {
            getScheduler().runAtEntity(entity, entity::remove);
        }
    }

    private static boolean shouldTeleport(Location current, Location target) {
        if (current == null || target == null) return false;
        return current.distanceSquared(target) > 0.0001;
    }

    private static float lineScaleX(HologramLine line) {
        return line == null ? 1.0f : Math.max(0.01f, line.getScaleX());
    }

    private static float lineScaleY(HologramLine line) {
        return line == null ? 1.0f : Math.max(0.01f, line.getScaleY());
    }

    private static float lineScaleZ(HologramLine line) {
        return line == null ? 1.0f : Math.max(0.01f, line.getScaleZ());
    }

    private static float resolveTextHorizontalScale() {
        if (pluginInstance == null) return 1.12f;
        return (float) Math.max(0.25d, Math.min(4.0d,
                pluginInstance.getConfig().getDouble("general.defaults.text-rendering.horizontal-scale", 1.12d)));
    }

    private static int resolveTextLineWidth() {
        if (pluginInstance == null) return 2048;
        return Math.max(1, Math.min(8192,
                pluginInstance.getConfig().getInt("general.defaults.text-rendering.line-width", 2048)));
    }

    private static AxoScheduler getScheduler() {
        if (schedulerInstance != null) {
            return schedulerInstance;
        }
        return AxoScheduler.create(pluginInstance);
    }

    private static void spawnInteractionIfNeeded(
            Player player,
            Hologram hologram,
            Location location,
            UUID viewerId,
            PacketViewerTracker.LineKey key
    ) {
        if (hologram.getActions(org.axostudio.axohologram.api.action.HologramClickType.ANY).isEmpty()
                && hologram.getActions(org.axostudio.axohologram.api.action.HologramClickType.LEFT).isEmpty()
                && hologram.getActions(org.axostudio.axohologram.api.action.HologramClickType.RIGHT).isEmpty()) {
            return;
        }
        Interaction interaction = DisplayPacketFactory.spawnInteraction(location, 1.2f, 0.4f);
        if (interaction == null) return;
        TRACKER.trackDisplay(interaction.getUniqueId(), viewerId, hologram.getId(), key.pageIndex(), key.lineIndex());
        TRACKER.getPlayerInteractions(viewerId).put(key, interaction.getUniqueId());
        if (pluginInstance != null) player.showEntity(pluginInstance, interaction);
    }
}
