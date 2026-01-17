package net.spookly.hyprox.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.spookly.hyprox.config.HyproxConfig;

/**
 * Runs periodic active probes and updates the backend health tracker.
 */
public final class BackendHealthProbeService implements AutoCloseable {
    private final HyproxConfig config;
    private final RoutingService routingService;
    private final BackendHealthTracker healthTracker;
    private final BackendHealthProbe probe;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledTask;

    /**
     * Create a probe service that executes active checks based on config.
     */
    public BackendHealthProbeService(HyproxConfig config,
                                     RoutingService routingService,
                                     BackendHealthTracker healthTracker,
                                     BackendHealthProbe probe) {
        this.config = Objects.requireNonNull(config, "config");
        this.routingService = Objects.requireNonNull(routingService, "routingService");
        this.healthTracker = Objects.requireNonNull(healthTracker, "healthTracker");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory());
    }

    /**
     * Start periodic probes when routing.health is configured.
     */
    public void start() {
        if (stopped.get() || !isEnabled() || scheduledTask != null) {
            return;
        }
        int intervalSeconds = intervalSeconds();
        if (intervalSeconds <= 0) {
            return;
        }
        scheduledTask = scheduler.scheduleAtFixedRate(this::runOnce, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Stop periodic probes and release probe resources.
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        scheduler.shutdownNow();
        try {
            probe.close();
        } catch (Exception e) {
            System.err.println("Failed to stop health probe: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        stop();
    }

    void runOnce() {
        if (stopped.get() || !isEnabled()) {
            return;
        }
        List<BackendTarget> targets = listTargets();
        if (targets.isEmpty()) {
            return;
        }
        int timeoutMs = timeoutMs();
        for (BackendTarget target : targets) {
            probeBackend(target, timeoutMs);
        }
    }

    private void probeBackend(BackendTarget target, int timeoutMs) {
        try {
            probe.probe(target, timeoutMs).whenComplete((success, error) -> {
                if (error != null || !Boolean.TRUE.equals(success)) {
                    healthTracker.recordActiveFailure(target);
                    return;
                }
                healthTracker.recordActiveSuccess(target);
            });
        } catch (RuntimeException e) {
            healthTracker.recordActiveFailure(target);
        }
    }

    private List<BackendTarget> listTargets() {
        HyproxConfig.RoutingConfig routing = config.routing;
        if (routing == null || routing.pools == null || routing.pools.isEmpty()) {
            return List.of();
        }
        List<BackendTarget> targets = new ArrayList<>();
        for (String pool : routing.pools.keySet()) {
            targets.addAll(routingService.listBackends(pool, false));
        }
        return targets;
    }

    private boolean isEnabled() {
        return config.routing != null && config.routing.health != null;
    }

    private int intervalSeconds() {
        HyproxConfig.HealthConfig health = config.routing == null ? null : config.routing.health;
        if (health == null || health.intervalSeconds == null) {
            return 0;
        }
        return health.intervalSeconds;
    }

    private int timeoutMs() {
        HyproxConfig.HealthConfig health = config.routing == null ? null : config.routing.health;
        if (health == null || health.timeoutMs == null) {
            return 0;
        }
        return health.timeoutMs;
    }

    private static ThreadFactory threadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "hyprox-health-probe");
            thread.setDaemon(true);
            return thread;
        };
    }
}
