package net.spookly.hyprox.registry;

import java.util.List;

public final class RegistryRequests {
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

    public static final class HeartbeatRequest {
        public String orchestratorId;
        public String backendId;
        public Integer ttlSeconds;
    }

    public static final class DrainRequest {
        public String orchestratorId;
        public String backendId;
        public Integer drainSeconds;
    }

    private RegistryRequests() {
    }
}
