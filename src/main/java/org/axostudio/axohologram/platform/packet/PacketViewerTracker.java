package org.axostudio.axohologram.platform.packet;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketViewerTracker {

    public record LineKey(String hologramId, int pageIndex, int lineIndex) {}

    public record TrackedDisplay(UUID viewerId, String hologramId, int pageIndex, int lineIndex) {}

    private final Map<UUID, Map<LineKey, UUID>> playerLineEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Map<LineKey, UUID>> playerLineInteractions = new ConcurrentHashMap<>();
    private final Map<UUID, TrackedDisplay> trackedDisplays = new ConcurrentHashMap<>();
    private final Set<UUID> trackedEntityIds = ConcurrentHashMap.newKeySet();

    public Map<LineKey, UUID> getPlayerEntities(UUID viewerId) {
        return playerLineEntities.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
    }

    public Map<LineKey, UUID> getPlayerInteractions(UUID viewerId) {
        return playerLineInteractions.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
    }

    public void trackDisplay(UUID entityId, UUID viewerId, String hologramId, int pageIndex, int lineIndex) {
        trackedEntityIds.add(entityId);
        trackedDisplays.put(entityId, new TrackedDisplay(viewerId, hologramId, pageIndex, lineIndex));
    }

    public TrackedDisplay getTrackedDisplay(UUID entityId) {
        return entityId == null ? null : trackedDisplays.get(entityId);
    }

    public boolean isTracked(UUID entityId) {
        return entityId != null && trackedEntityIds.contains(entityId);
    }

    public void untrack(UUID entityId) {
        if (entityId != null) {
            trackedEntityIds.remove(entityId);
            trackedDisplays.remove(entityId);
        }
    }

    public void clearPlayer(UUID viewerId) {
        Map<LineKey, UUID> entities = playerLineEntities.remove(viewerId);
        if (entities != null) {
            for (UUID id : entities.values()) {
                untrack(id);
            }
        }
        Map<LineKey, UUID> interactions = playerLineInteractions.remove(viewerId);
        if (interactions != null) {
            for (UUID id : interactions.values()) {
                untrack(id);
            }
        }
    }

    public void clearAll() {
        playerLineEntities.clear();
        playerLineInteractions.clear();
        trackedDisplays.clear();
        trackedEntityIds.clear();
    }
}
