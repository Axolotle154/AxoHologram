package org.axostudio.axohologram.platform.packet;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketViewerTrackerTest {

    @Test
    void onlyLatestSpawnReservationCanBeCommitted() {
        PacketViewerTracker tracker = new PacketViewerTracker();
        UUID viewerId = UUID.randomUUID();
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey("vip", 0, -1);

        long staleToken = tracker.reserveSpawn(viewerId, key);
        long currentToken = tracker.reserveSpawn(viewerId, key);

        assertFalse(tracker.commitSpawn(
                viewerId, key, staleToken, UUID.randomUUID(), "vip", 0, -1
        ));
        assertTrue(tracker.commitSpawn(
                viewerId, key, currentToken, UUID.randomUUID(), "vip", 0, -1
        ));
    }

    @Test
    void cancellingLineInvalidatesPendingSpawn() {
        PacketViewerTracker tracker = new PacketViewerTracker();
        UUID viewerId = UUID.randomUUID();
        PacketViewerTracker.LineKey key = new PacketViewerTracker.LineKey("vip", 0, -1);
        long token = tracker.reserveSpawn(viewerId, key);

        tracker.cancelSpawn(viewerId, key);

        assertFalse(tracker.isSpawnCurrent(viewerId, key, token));
        assertFalse(tracker.commitSpawn(
                viewerId, key, token, UUID.randomUUID(), "vip", 0, -1
        ));
    }
}
