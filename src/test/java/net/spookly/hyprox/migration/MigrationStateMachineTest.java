package net.spookly.hyprox.migration;

import net.spookly.hyprox.config.HyproxConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationStateMachineTest {
    @Test
    void completesMigrationFlow() {
        HyproxConfig config = buildConfig(1000, 1000);
        MigrationStateMachine machine = new MigrationStateMachine(config, fixedClock());

        MigrationStateMachine.TransitionResult start = machine.start(context());
        assertTrue(start.ok());
        assertEquals(MigrationPhase.PREPARE, machine.phase());

        assertTrue(machine.markPrepared().ok());
        assertEquals(MigrationPhase.AUTH, machine.phase());

        assertTrue(machine.markAuthComplete().ok());
        assertEquals(MigrationPhase.SYNC, machine.phase());

        assertTrue(machine.markSyncComplete().ok());
        assertEquals(MigrationPhase.FREEZE, machine.phase());

        assertTrue(machine.markFrozen().ok());
        assertEquals(MigrationPhase.CUTOVER, machine.phase());

        assertTrue(machine.markCutoverComplete().ok());
        assertEquals(MigrationPhase.RESUME, machine.phase());

        assertTrue(machine.markResumed().ok());
        assertEquals(MigrationPhase.CLEANUP, machine.phase());

        assertTrue(machine.markCleanupComplete().ok());
        assertEquals(MigrationPhase.IDLE, machine.phase());
        assertNull(machine.context());
    }

    @Test
    void rejectsOutOfOrderTransition() {
        HyproxConfig config = buildConfig(1000, 1000);
        MigrationStateMachine machine = new MigrationStateMachine(config, fixedClock());

        machine.start(context());
        MigrationStateMachine.TransitionResult result = machine.markSyncComplete();

        assertFalse(result.ok());
        assertEquals(MigrationPhase.PREPARE, machine.phase());
        assertEquals("migration expected SYNC but was PREPARE", result.error());
    }

    @Test
    void failsPrepareTimeout() {
        MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        HyproxConfig config = buildConfig(1000, 1000);
        MigrationStateMachine machine = new MigrationStateMachine(config, clock);

        machine.start(context());
        clock.advance(Duration.ofMillis(1500));

        MigrationStateMachine.TransitionResult result = machine.checkTimeouts();

        assertFalse(result.ok());
        assertEquals(MigrationPhase.FAILED, machine.phase());
        assertEquals("migration prepare timeout", result.error());
        assertEquals("migration prepare timeout", machine.failureReason());
    }

    @Test
    void failsCutoverTimeout() {
        MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        HyproxConfig config = buildConfig(1000, 1000);
        MigrationStateMachine machine = new MigrationStateMachine(config, clock);

        machine.start(context());
        machine.markPrepared();
        machine.markAuthComplete();
        machine.markSyncComplete();
        clock.advance(Duration.ofMillis(1500));

        MigrationStateMachine.TransitionResult result = machine.checkTimeouts();

        assertFalse(result.ok());
        assertEquals(MigrationPhase.FAILED, machine.phase());
        assertEquals("migration cutover timeout", result.error());
    }

    @Test
    void recordsMetricsOnSuccessAndFailure() {
        MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        MigrationMetrics metrics = new MigrationMetrics();
        HyproxConfig config = buildConfig(1000, 1000);
        MigrationStateMachine machine = new MigrationStateMachine(config, metrics, clock);

        machine.start(context());
        machine.markPrepared();
        machine.markAuthComplete();
        machine.markSyncComplete();
        machine.markFrozen();
        machine.markCutoverComplete();
        machine.markResumed();
        clock.advance(Duration.ofMillis(500));
        machine.markCleanupComplete();

        assertEquals(1, metrics.total());
        assertEquals(1, metrics.success());
        assertEquals(0, metrics.failure());
        assertEquals(500, metrics.lastDurationMs());

        machine.start(context());
        clock.advance(Duration.ofMillis(1500));
        MigrationStateMachine.TransitionResult result = machine.checkTimeouts();

        assertFalse(result.ok());
        assertEquals(2, metrics.total());
        assertEquals(1, metrics.failure());
        assertEquals("migration prepare timeout", metrics.lastFailureReason());
        assertEquals(1500, metrics.lastDurationMs());
        assertEquals(1L, metrics.failureCountsByReason().get("migration prepare timeout"));
    }

    private HyproxConfig buildConfig(Integer prepareTimeoutMs, Integer cutoverTimeoutMs) {
        HyproxConfig config = new HyproxConfig();
        config.migration = new HyproxConfig.MigrationConfig();
        config.migration.enabled = true;
        config.migration.prepareTimeoutMs = prepareTimeoutMs;
        config.migration.cutoverTimeoutMs = cutoverTimeoutMs;
        return config;
    }

    private MigrationContext context() {
        return new MigrationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                "backend-a",
                "backend-b"
        );
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
