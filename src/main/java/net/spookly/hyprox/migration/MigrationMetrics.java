package net.spookly.hyprox.migration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory counters for migration attempts and outcomes.
 */
public final class MigrationMetrics {
    private static final String UNKNOWN_REASON = "unknown";

    /**
     * Total number of migration attempts recorded.
     */
    private final AtomicLong total = new AtomicLong();
    /**
     * Number of successful migrations.
     */
    private final AtomicLong success = new AtomicLong();
    /**
     * Number of failed migrations.
     */
    private final AtomicLong failure = new AtomicLong();
    /**
     * Sum of migration durations in milliseconds.
     */
    private final AtomicLong totalDurationMs = new AtomicLong();
    /**
     * Duration of the most recently recorded migration.
     */
    private final AtomicLong lastDurationMs = new AtomicLong();
    /**
     * Failure reason for the most recent failed migration.
     */
    private final AtomicReference<String> lastFailureReason = new AtomicReference<>();
    /**
     * Failure counts grouped by reason.
     */
    private final Map<String, AtomicLong> failuresByReason = new ConcurrentHashMap<>();

    public void recordSuccess(long durationMs) {
        long normalizedDuration = normalizeDuration(durationMs);
        total.incrementAndGet();
        success.incrementAndGet();
        totalDurationMs.addAndGet(normalizedDuration);
        lastDurationMs.set(normalizedDuration);
    }

    public void recordFailure(String reason, long durationMs) {
        String normalizedReason = normalizeReason(reason);
        long normalizedDuration = normalizeDuration(durationMs);
        total.incrementAndGet();
        failure.incrementAndGet();
        totalDurationMs.addAndGet(normalizedDuration);
        lastDurationMs.set(normalizedDuration);
        lastFailureReason.set(normalizedReason);
        failuresByReason.computeIfAbsent(normalizedReason, ignored -> new AtomicLong()).incrementAndGet();
    }

    public long total() {
        return total.get();
    }

    public long success() {
        return success.get();
    }

    public long failure() {
        return failure.get();
    }

    public long totalDurationMs() {
        return totalDurationMs.get();
    }

    public long lastDurationMs() {
        return lastDurationMs.get();
    }

    public String lastFailureReason() {
        return lastFailureReason.get();
    }

    public Map<String, Long> failureCountsByReason() {
        if (failuresByReason.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> snapshot = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : failuresByReason.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    private long normalizeDuration(long durationMs) {
        return Math.max(durationMs, 0L);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return UNKNOWN_REASON;
        }
        return reason.trim();
    }
}
