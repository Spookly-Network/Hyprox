package net.spookly.hyprox.registry;

import java.util.List;

/**
 * Request payloads for registry API endpoints.
 */
public final class RegistryRequests {
    /**
     * Register a new backend from an orchestrator.
     */
    public static final class RegisterRequest {
        public String orchestratorId;
        public String pool;
        public String backendId;
        public String host;
        public Integer port;
        public Integer weight;
        public Integer maxPlayers;
        public List<String> tags;
        public Integer ttlSeconds;
    }

    /**
     * Heartbeat payload for an existing backend.
     */
    public static final class HeartbeatRequest {
        public String orchestratorId;
        public String backendId;
        public Integer ttlSeconds;
    }

    /**
     * Drain request for a backend.
     */
    public static final class DrainRequest {
        public String orchestratorId;
        public String backendId;
        public Integer drainSeconds;
    }

    private RegistryRequests() {
    }
}
