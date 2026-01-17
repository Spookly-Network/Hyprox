package net.spookly.hyprox.routing;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Represents a reserved capacity slot for a backend target.
 */
@Getter
@Accessors(fluent = true)
public final class BackendReservation implements AutoCloseable {
    private final BackendTarget backend;
    private final BackendCapacityTracker tracker;
    private final String key;
    private final AtomicBoolean released = new AtomicBoolean(false);

    private BackendReservation(BackendTarget backend, BackendCapacityTracker tracker, String key) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.tracker = tracker;
        this.key = key;
    }

    /**
     * Create a reservation backed by a capacity tracker.
     */
    static BackendReservation tracked(BackendTarget backend, BackendCapacityTracker tracker, String key) {
        return new BackendReservation(backend, tracker, key);
    }

    /**
     * Create a reservation that does not affect capacity counts.
     */
    static BackendReservation unlimited(BackendTarget backend) {
        return new BackendReservation(backend, null, null);
    }

    /**
     * Releases the reservation back to the capacity tracker.
     */
    public void release() {
        if (tracker == null) {
            return;
        }
        if (released.compareAndSet(false, true)) {
            tracker.release(key);
        }
    }

    @Override
    public void close() {
        release();
    }
}
