package cn.lunadeer.dominion.uis.menu.route;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prevents a slower asynchronous menu request from replacing a newer player route.
 */
public final class UiRequestTracker {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<UUID, Long> activeRequests = new ConcurrentHashMap<>();

    /**
     * Creates an independent latest-request tracker for one renderer surface.
     */
    public UiRequestTracker() {
    }

    /**
     * Starts and returns the new active request id for one player.
     */
    public long begin(UUID playerId) {
        long requestId = sequence.incrementAndGet();
        activeRequests.put(playerId, requestId);
        return requestId;
    }

    /**
     * Returns whether the request remains the newest request for its player.
     */
    public boolean isActive(UUID playerId, long requestId) {
        return activeRequests.getOrDefault(playerId, -1L) == requestId;
    }

    /**
     * Removes a completed request only when it is still the active request.
     */
    public void complete(UUID playerId, long requestId) {
        activeRequests.remove(playerId, requestId);
    }
}
