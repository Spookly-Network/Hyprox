package net.spookly.hyprox.registry;

import java.time.Instant;
import java.util.List;

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

    public RegisteredBackend(String id,
                             String pool,
                             String host,
                             int port,
                             Integer weight,
                             Integer maxPlayers,
                             List<String> tags,
                             String orchestratorId,
                             Instant lastHeartbeat,
                             Instant expiresAt,
                             boolean draining) {
        this.id = id;
        this.pool = pool;
        this.host = host;
        this.port = port;
        this.weight = weight;
        this.maxPlayers = maxPlayers;
        this.tags = tags;
        this.orchestratorId = orchestratorId;
        this.lastHeartbeat = lastHeartbeat;
        this.expiresAt = expiresAt;
        this.draining = draining;
    }

    public String id() {
        return id;
    }

    public String pool() {
        return pool;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public Integer weight() {
        return weight;
    }

    public Integer maxPlayers() {
        return maxPlayers;
    }

    public List<String> tags() {
        return tags;
    }

    public String orchestratorId() {
        return orchestratorId;
    }

    public Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean draining() {
        return draining;
    }

    public void markHeartbeat(Instant now, Instant expiresAt) {
        this.lastHeartbeat = now;
        this.expiresAt = expiresAt;
    }

    public void markDraining(Instant now, Instant expiresAt) {
        this.draining = true;
        this.lastHeartbeat = now;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
