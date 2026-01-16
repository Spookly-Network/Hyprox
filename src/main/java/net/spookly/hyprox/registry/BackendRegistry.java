package net.spookly.hyprox.registry;

import lombok.NonNull;
import net.spookly.hyprox.config.HyproxConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory store for dynamic backend registrations with TTL and drain support.
 */
public final class BackendRegistry {
    private final Map<String, RegisteredBackend> backends = new ConcurrentHashMap<>();
    private final Set<String> staticBackendIds;
    private final int defaultTtlSeconds;
    private final int heartbeatGraceSeconds;
    private final int drainTimeoutSeconds;
    private final RegistryEventListener eventListener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Create a registry with explicit defaults.
     */
    public BackendRegistry(Set<String> staticBackendIds, int defaultTtlSeconds, int heartbeatGraceSeconds, int drainTimeoutSeconds) {
        this(staticBackendIds, defaultTtlSeconds, heartbeatGraceSeconds, drainTimeoutSeconds, RegistryEventListener.NOOP);
    }

    /**
     * Create a registry with explicit defaults and an audit listener.
     */
    public BackendRegistry(Set<String> staticBackendIds,
                           int defaultTtlSeconds,
                           int heartbeatGraceSeconds,
                           int drainTimeoutSeconds,
                           RegistryEventListener eventListener) {
        this.staticBackendIds = staticBackendIds;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.heartbeatGraceSeconds = heartbeatGraceSeconds;
        this.drainTimeoutSeconds = drainTimeoutSeconds;
        this.eventListener = eventListener == null ? RegistryEventListener.NOOP : eventListener;
    }

    /**
     * Build a registry with defaults extracted from config.
     */
    public static BackendRegistry fromConfig(HyproxConfig config) {
        return fromConfig(config, RegistryEventListener.NOOP);
    }

    /**
     * Build a registry with defaults extracted from config and an audit listener.
     */
    public static BackendRegistry fromConfig(HyproxConfig config, RegistryEventListener eventListener) {
        Set<String> staticIds = RegistryUtils.collectStaticBackendIds(config);
        int ttlSeconds = 30;
        int graceSeconds = 10;
        int drainSeconds = 60;
        if (config.registry != null && config.registry.defaults != null) {
            if (config.registry.defaults.ttlSeconds != null) {
                ttlSeconds = config.registry.defaults.ttlSeconds;
            }
            if (config.registry.defaults.heartbeatGraceSeconds != null) {
                graceSeconds = config.registry.defaults.heartbeatGraceSeconds;
            }
            if (config.registry.defaults.drainTimeoutSeconds != null) {
                drainSeconds = config.registry.defaults.drainTimeoutSeconds;
            }
        }
        return new BackendRegistry(staticIds, ttlSeconds, graceSeconds, drainSeconds, eventListener);
    }

    /**
     * Start background cleanup of expired entries.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::purgeExpired, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Stop background cleanup.
     */
    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * Register or refresh a backend entry.
     */
    public RegisteredBackend register(@NonNull RegisteredBackend backend, Integer ttlSecondsOverride) {
        if (staticBackendIds.contains(backend.id())) {
            throw new IllegalArgumentException("backend id conflicts with static backend: " + backend.id());
        }
        Instant now = Instant.now();
        int ttlSeconds = ttlSecondsOverride != null ? Math.min(ttlSecondsOverride, defaultTtlSeconds) : defaultTtlSeconds;
        if (ttlSeconds <= 0) {
            ttlSeconds = defaultTtlSeconds;
        }
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        RegisteredBackend stored = new RegisteredBackend(
                backend.id(),
                backend.pool(),
                backend.host(),
                backend.port(),
                backend.weight(),
                backend.maxPlayers(),
                backend.tags(),
                backend.orchestratorId(),
                now,
                expiresAt,
                false
        );
        RegisteredBackend existing = backends.putIfAbsent(backend.id(), stored);
        if (existing != null) {
            if (!existing.orchestratorId().equals(backend.orchestratorId())) {
                throw new IllegalArgumentException("backend id already registered by another orchestrator");
            }
            existing.markHeartbeat(now, expiresAt);
            emit(RegistryEventType.REGISTER, existing, now);
            return existing;
        }
        emit(RegistryEventType.REGISTER, stored, now);
        return stored;
    }

    public RegisteredBackend register(RegisteredBackend backend) {
        return register(backend, null);
    }

    /**
     * Update the heartbeat for a registered backend.
     */
    public RegisteredBackend heartbeat(@NonNull String backendId, @NonNull String orchestratorId, Integer ttlSecondsOverride) {
        RegisteredBackend backend = backends.get(backendId);
        if (backend == null) {
            throw new IllegalArgumentException("backend id not found");
        }
        if (!backend.orchestratorId().equals(orchestratorId)) {
            throw new IllegalArgumentException("backend id not owned by orchestrator");
        }
        Instant now = Instant.now();
        int ttlSeconds = ttlSecondsOverride != null ? Math.min(ttlSecondsOverride, defaultTtlSeconds) : defaultTtlSeconds;
        if (ttlSeconds <= 0) {
            ttlSeconds = defaultTtlSeconds;
        }
        backend.markHeartbeat(now, now.plusSeconds(ttlSeconds));
        emit(RegistryEventType.HEARTBEAT, backend, now);
        return backend;
    }

    /**
     * Mark a backend as draining and extend its expiry window.
     */
    public RegisteredBackend drain(@NonNull String backendId, @NonNull String orchestratorId, Integer drainSecondsOverride) {
        RegisteredBackend backend = backends.get(backendId);
        if (backend == null) {
            throw new IllegalArgumentException("backend id not found");
        }
        if (!backend.orchestratorId().equals(orchestratorId)) {
            throw new IllegalArgumentException("backend id not owned by orchestrator");
        }
        int drainSeconds = drainSecondsOverride != null
                ? Math.min(drainSecondsOverride, drainTimeoutSeconds)
                : drainTimeoutSeconds;
        if (drainSeconds <= 0) {
            drainSeconds = drainTimeoutSeconds;
        }
        Instant now = Instant.now();
        backend.markDraining(now, now.plusSeconds(drainSeconds));
        emit(RegistryEventType.DRAIN, backend, now);
        return backend;
    }

    /**
     * List all registered backends, optionally filtered by pool.
     */
    public List<RegisteredBackend> list(String pool) {
        if (pool == null || pool.trim().isEmpty()) {
            return new ArrayList<>(backends.values());
        }
        List<RegisteredBackend> filtered = new ArrayList<>();
        for (RegisteredBackend backend : backends.values()) {
            if (pool.equalsIgnoreCase(backend.pool())) {
                filtered.add(backend);
            }
        }
        return filtered;
    }

    /**
     * Total dynamic backend count.
     */
    public int size() {
        return backends.size();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(heartbeatGraceSeconds);
        for (Map.Entry<String, RegisteredBackend> entry : backends.entrySet()) {
            RegisteredBackend backend = entry.getValue();
            if (backend.isExpired(cutoff)) {
                if (backends.remove(entry.getKey(), backend)) {
                    emit(RegistryEventType.EXPIRE, backend, now);
                }
            }
        }
    }

    private void emit(RegistryEventType type, RegisteredBackend backend, Instant now) {
        try {
            eventListener.onEvent(RegistryEvent.from(type, backend, now));
        } catch (RuntimeException e) {
            System.err.println("Failed to emit registry audit event: " + e.getMessage());
        }
    }

    public int defaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public int drainTimeoutSeconds() {
        return drainTimeoutSeconds;
    }

    /**
     * Grace window for heartbeat expiry.
     */
    public int heartbeatGraceSeconds() {
        return heartbeatGraceSeconds;
    }

    public Set<String> staticBackendIds() {
        return Collections.unmodifiableSet(staticBackendIds);
    }
}
