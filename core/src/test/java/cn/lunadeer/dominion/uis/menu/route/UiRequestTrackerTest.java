package cn.lunadeer.dominion.uis.menu.route;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiRequestTrackerTest {

    @Test
    void olderCompletionCannotRetireNewerRequest() {
        UiRequestTracker tracker = new UiRequestTracker();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        long first = tracker.begin(playerId);
        long second = tracker.begin(playerId);
        tracker.complete(playerId, first);

        assertFalse(tracker.isActive(playerId, first));
        assertTrue(tracker.isActive(playerId, second));
        tracker.complete(playerId, second);
        assertFalse(tracker.isActive(playerId, second));
    }
}
