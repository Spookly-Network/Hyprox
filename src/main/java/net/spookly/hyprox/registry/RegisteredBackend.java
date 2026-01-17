package net.spookly.hyprox.registry;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Mutable runtime view of a backend registered by an orchestrator.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class RegisteredBackend {
    private final String id;
    private final String pool;
    private final String host;
    private final int port;
    private final Integer weight;
    private final Integer maxPlayers;
    private final List<String> tags;
    private final String orchestratorId;
    private volatile Instant lastHeartbeat;
    private volatile Instant expiresAt;
    private volatile boolean draining;

    /**
     * Update heartbeat/expiry timestamps.
     */
    public void markHeartbeat(Instant now, Instant expiresAt) {
        this.lastHeartbeat = now;
        this.expiresAt = expiresAt;
    }

    /**
     * Mark this backend as draining and extend expiry window.
     */
    public void markDraining(Instant now, Instant expiresAt) {
        this.draining = true;
        this.lastHeartbeat = now;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns true when the backend expiry is before the provided timestamp.
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
