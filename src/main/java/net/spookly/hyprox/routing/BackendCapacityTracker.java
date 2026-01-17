package net.spookly.hyprox.routing;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks per-backend capacity reservations using configured max player counts.
 */
public final class BackendCapacityTracker {
    private final ConcurrentMap<String, AtomicInteger> activeCounts = new ConcurrentHashMap<>();

    /**
     * Attempt to reserve capacity for the provided backend target.
     */
    public BackendReservation tryReserve(BackendTarget target) {
        Objects.requireNonNull(target, "target");
        Integer maxPlayers = target.maxPlayers();
        if (maxPlayers == null || maxPlayers <= 0) {
            return BackendReservation.unlimited(target);
        }
        String key = backendKey(target);
        if (key == null) {
            return BackendReservation.unlimited(target);
        }
        AtomicInteger counter = activeCounts.computeIfAbsent(key, ignored -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= maxPlayers) {
                return null;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return BackendReservation.tracked(target, this, key);
            }
        }
    }

    void release(String key) {
        if (key == null) {
            return;
        }
        AtomicInteger counter = activeCounts.get(key);
        if (counter == null) {
            return;
        }
        int remaining = counter.decrementAndGet();
        if (remaining <= 0) {
            activeCounts.remove(key, counter);
        }
    }

    private String backendKey(BackendTarget target) {
        String base = normalizeId(target.id());
        if (base == null) {
            base = normalizeHostPort(target.host(), target.port());
        }
        if (base == null) {
            return null;
        }
        String pool = normalizeId(target.pool());
        if (pool == null) {
            return base;
        }
        return pool + ":" + base;
    }

    private String normalizeId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private String normalizeHostPort(String host, int port) {
        if (host == null) {
            return null;
        }
        String trimmed = host.trim();
        if (trimmed.isEmpty() || port <= 0) {
            return null;
        }
        return trimmed + ":" + port;
    }
}
