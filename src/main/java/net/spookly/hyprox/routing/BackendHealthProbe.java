package net.spookly.hyprox.routing;

import java.util.concurrent.CompletableFuture;

/**
 * Executes an active probe against a backend to determine reachability.
 */
public interface BackendHealthProbe extends AutoCloseable {
    /**
     * Probe the target backend and complete with true when the probe succeeds.
     */
    CompletableFuture<Boolean> probe(BackendTarget target, int timeoutMs);

    @Override
    default void close() {
    }
}
