package net.spookly.hyprox.registry;

import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Snapshot of a registry state change for audit logging.
 */
@Value
@Accessors(fluent = true)
public class RegistryEvent {
    RegistryEventType type;
    Instant timestamp;
    String backendId;
    String pool;
    String host;
    int port;
    Integer weight;
    Integer maxPlayers;
    String orchestratorId;
    Instant lastHeartbeat;
    Instant expiresAt;
    boolean draining;

    public static RegistryEvent from(RegistryEventType type, RegisteredBackend backend, Instant timestamp) {
        return new RegistryEvent(
                type,
                timestamp,
                backend.id(),
                backend.pool(),
                backend.host(),
                backend.port(),
                backend.weight(),
                backend.maxPlayers(),
                backend.orchestratorId(),
                backend.lastHeartbeat(),
                backend.expiresAt(),
                backend.draining()
        );
    }
}
