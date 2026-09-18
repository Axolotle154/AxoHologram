package org.axostudio.axohologram.platform.packet;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PacketViewerTracker {

    public record LineKey(String hologramId, int pageIndex, int lineIndex) {}

    public record TrackedDisplay(UUID viewerId, String hologramId, int pageIndex, int lineIndex) {}

    private final Map<UUID, Map<LineKey, UUID>> playerLineEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Map<LineKey, UUID>> playerLineInteractions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<LineKey, Long>> pendingLineSpawns = new ConcurrentHashMap<>();
    private final Map<UUID, TrackedDisplay> trackedDisplays = new ConcurrentHashMap<>();
    private final Set<UUID> trackedEntityIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong spawnSequence = new AtomicLong();

    public Map<LineKey, UUID> getPlayerEntities(UUID viewerId) {
        return playerLineEntities.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
    }

    public Map<LineKey, UUID> getPlayerInteractions(UUID viewerId) {
        return playerLineInteractions.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
    }

    /**
     * Reserves a new spawn for this viewer and line. Any older scheduled spawn
     * for the same key becomes stale and must not be committed.
     */
    public long reserveSpawn(UUID viewerId, LineKey key) {
        Map<LineKey, Long> pending = pendingLineSpawns.computeIfAbsent(
                viewerId,
                ignored -> new ConcurrentHashMap<>()
        );
        synchronized (pending) {
            long token = spawnSequence.incrementAndGet();
            pending.put(key, token);
            return token;
        }
    }

    public boolean isSpawnCurrent(UUID viewerId, LineKey key, long token) {
        Map<LineKey, Long> pending = pendingLineSpawns.get(viewerId);
        if (pending == null) return false;
        synchronized (pending) {
            return Long.valueOf(token).equals(pending.get(key));
        }
    }

    /**
     * Atomically publishes a spawned entity only if its reservation is still
     * current. This prevents an older scheduled task from becoming an orphan.
     */
    public boolean commitSpawn(
            UUID viewerId,
            LineKey key,
            long token,
            UUID entityId,
            String hologramId,
            int pageIndex,
            int lineIndex
    ) {
        Map<LineKey, Long> pending = pendingLineSpawns.get(viewerId);
        if (pending == null) return false;
        synchronized (pending) {
            if (!Long.valueOf(token).equals(pending.get(key))) return false;
            pending.remove(key);
            getPlayerEntities(viewerId).put(key, entityId);
            trackDisplay(entityId, viewerId, hologramId, pageIndex, lineIndex);
            return true;
        }
    }

    public void cancelSpawn(UUID viewerId, LineKey key) {
        Map<LineKey, Long> pending = pendingLineSpawns.get(viewerId);
        if (pending == null) return;
        synchronized (pending) {
            pending.remove(key);
        }
    }

    public void cancelSpawn(UUID viewerId, LineKey key, long token) {
        Map<LineKey, Long> pending = pendingLineSpawns.get(viewerId);
        if (pending == null) return;
        synchronized (pending) {
            if (Long.valueOf(token).equals(pending.get(key))) {
                pending.remove(key);
            }
        }
    }

    public void cancelSpawnsForHologram(UUID viewerId, String hologramId) {
        removePendingSpawns(viewerId, key -> key.hologramId().equals(hologramId));
    }

    public void cancelSpawnsForOtherPages(UUID viewerId, String hologramId, int visiblePageIndex) {
        removePendingSpawns(viewerId, key ->
                key.hologramId().equals(hologramId) && key.pageIndex() != visiblePageIndex
        );
    }

    public void cancelSpawnsExcept(UUID viewerId, String hologramId, int pageIndex, Set<Integer> keepLines) {
        removePendingSpawns(viewerId, key ->
                key.hologramId().equals(hologramId)
                        && key.pageIndex() == pageIndex
                        && (keepLines == null || !keepLines.contains(key.lineIndex()))
        );
    }

    private void removePendingSpawns(UUID viewerId, java.util.function.Predicate<LineKey> predicate) {
        Map<LineKey, Long> pending = pendingLineSpawns.get(viewerId);
        if (pending == null) return;
        synchronized (pending) {
            pending.keySet().removeIf(predicate);
        }
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
        pendingLineSpawns.remove(viewerId);
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
        pendingLineSpawns.clear();
        trackedDisplays.clear();
        trackedEntityIds.clear();
    }
}
