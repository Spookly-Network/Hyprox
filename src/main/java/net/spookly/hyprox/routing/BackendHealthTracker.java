package net.spookly.hyprox.routing;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks backend health scores from passive failures and optional active probes.
 */
public final class BackendHealthTracker {
    private static final int MAX_SCORE = 100;
    private static final int MIN_SCORE = 0;
    private static final int UNHEALTHY_THRESHOLD = 40;
    private static final int PASSIVE_FAILURE_PENALTY = 25;
    private static final int ACTIVE_FAILURE_PENALTY = 20;
    private static final int PASSIVE_SUCCESS_REWARD = 25;
    private static final int ACTIVE_SUCCESS_REWARD = 20;

    private final Map<String, HealthState> states = new ConcurrentHashMap<>();

    /**
     * Record a passive failure (connect timeout, handshake error, etc.).
     */
    public void recordPassiveFailure(BackendTarget target) {
        updateScore(target, -PASSIVE_FAILURE_PENALTY, true, false);
    }

    /**
     * Record a passive success (connection established).
     */
    public void recordPassiveSuccess(BackendTarget target) {
        updateScore(target, PASSIVE_SUCCESS_REWARD, false, false);
    }

    /**
     * Record an active probe failure.
     */
    public void recordActiveFailure(BackendTarget target) {
        updateScore(target, -ACTIVE_FAILURE_PENALTY, true, true);
    }

    /**
     * Record an active probe success.
     */
    public void recordActiveSuccess(BackendTarget target) {
        updateScore(target, ACTIVE_SUCCESS_REWARD, false, true);
    }

    /**
     * Current health score for a backend (0-100).
     */
    public int score(BackendTarget target) {
        String id = backendId(target);
        if (id == null) {
            return MAX_SCORE;
        }
        HealthState state = states.get(id);
        return state == null ? MAX_SCORE : state.score;
    }

    /**
     * True when a backend's score is above the unhealthy threshold.
     */
    public boolean isHealthy(BackendTarget target) {
        return score(target) >= UNHEALTHY_THRESHOLD;
    }

    private void updateScore(BackendTarget target, int delta, boolean failure, boolean active) {
        String id = backendId(target);
        if (id == null) {
            return;
        }
        Instant now = Instant.now();
        states.compute(id, (key, state) -> {
            HealthState current = state == null ? new HealthState() : state;
            current.apply(delta, failure, active, now);
            return current;
        });
    }

    private String backendId(BackendTarget target) {
        if (target == null || target.id() == null || target.id().trim().isEmpty()) {
            return null;
        }
        return target.id();
    }

    private static final class HealthState {
        private volatile int score = MAX_SCORE;
        private volatile int consecutiveFailures;
        private volatile Instant lastFailure;
        private volatile Instant lastSuccess;
        private volatile Instant lastActiveCheck;

        private void apply(int delta, boolean failure, boolean active, Instant now) {
            int nextScore = score + delta;
            if (nextScore > MAX_SCORE) {
                nextScore = MAX_SCORE;
            } else if (nextScore < MIN_SCORE) {
                nextScore = MIN_SCORE;
            }
            score = nextScore;
            if (failure) {
                consecutiveFailures++;
                lastFailure = now;
            } else {
                consecutiveFailures = 0;
                lastSuccess = now;
            }
            if (active) {
                lastActiveCheck = now;
            }
        }
    }
}
