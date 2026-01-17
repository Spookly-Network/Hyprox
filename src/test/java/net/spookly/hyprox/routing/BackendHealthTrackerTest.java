package net.spookly.hyprox.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendHealthTrackerTest {
    @Test
    void marksBackendUnhealthyAfterFailures() {
        BackendHealthTracker tracker = new BackendHealthTracker();
        BackendTarget target = backendTarget("backend-1");

        tracker.recordPassiveFailure(target);
        tracker.recordPassiveFailure(target);
        tracker.recordPassiveFailure(target);

        assertFalse(tracker.isHealthy(target));
    }

    @Test
    void recoversAfterSuccess() {
        BackendHealthTracker tracker = new BackendHealthTracker();
        BackendTarget target = backendTarget("backend-1");

        tracker.recordPassiveFailure(target);
        tracker.recordPassiveFailure(target);
        tracker.recordPassiveFailure(target);
        tracker.recordPassiveSuccess(target);

        assertTrue(tracker.isHealthy(target));
    }

    private BackendTarget backendTarget(String id) {
        return new BackendTarget(
                id,
                "pool-1",
                "10.0.0.1",
                9000,
                1,
                150,
                List.of("static"),
                BackendSource.STATIC,
                false
        );
    }
}
