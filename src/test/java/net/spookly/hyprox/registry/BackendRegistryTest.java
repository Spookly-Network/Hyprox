package net.spookly.hyprox.registry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendRegistryTest {
    @Test
    void rejectsStaticBackendId() {
        BackendRegistry registry = new BackendRegistry(Set.of("static-1"), 30, 5, 60);
        RegisteredBackend backend = new RegisteredBackend(
                "static-1",
                "lobby",
                "10.0.0.1",
                9000,
                1,
                100,
                List.of("lobby"),
                "orch-1",
                Instant.now(),
                Instant.now(),
                false
        );
        assertThrows(IllegalArgumentException.class, () -> registry.register(backend));
    }

    @Test
    void registersHeartbeatAndDrain() {
        BackendRegistry registry = new BackendRegistry(Set.of(), 30, 5, 60);
        RegisteredBackend backend = new RegisteredBackend(
                "dyn-1",
                "lobby",
                "10.0.0.2",
                9000,
                1,
                100,
                List.of("lobby"),
                "orch-1",
                Instant.now(),
                Instant.now(),
                false
        );
        RegisteredBackend stored = registry.register(backend);
        assertEquals("dyn-1", stored.id());

        RegisteredBackend heartbeat = registry.heartbeat("dyn-1", "orch-1", null);
        assertNotNull(heartbeat.expiresAt());

        RegisteredBackend drained = registry.drain("dyn-1", "orch-1", 10);
        assertTrue(drained.draining());
    }

    @Test
    void capsRegisterTtlToDefault() {
        BackendRegistry registry = new BackendRegistry(Set.of(), 30, 5, 60);
        RegisteredBackend backend = new RegisteredBackend(
                "dyn-ttl",
                "lobby",
                "10.0.0.9",
                9000,
                1,
                100,
                List.of("lobby"),
                "orch-1",
                Instant.now(),
                Instant.now(),
                false
        );
        RegisteredBackend stored = registry.register(backend, 120);
        assertEquals(30, Duration.between(stored.lastHeartbeat(), stored.expiresAt()).getSeconds());
    }

    @Test
    void usesHeartbeatOverrideWhenLowerThanDefault() {
        BackendRegistry registry = new BackendRegistry(Set.of(), 30, 5, 60);
        RegisteredBackend backend = new RegisteredBackend(
                "dyn-heartbeat",
                "lobby",
                "10.0.0.10",
                9000,
                1,
                100,
                List.of("lobby"),
                "orch-1",
                Instant.now(),
                Instant.now(),
                false
        );
        registry.register(backend);
        RegisteredBackend heartbeat = registry.heartbeat("dyn-heartbeat", "orch-1", 12);
        assertEquals(12, Duration.between(heartbeat.lastHeartbeat(), heartbeat.expiresAt()).getSeconds());
    }

    @Test
    void capsDrainOverrideToDefault() {
        BackendRegistry registry = new BackendRegistry(Set.of(), 30, 5, 60);
        RegisteredBackend backend = new RegisteredBackend(
                "dyn-drain",
                "lobby",
                "10.0.0.11",
                9000,
                1,
                100,
                List.of("lobby"),
                "orch-1",
                Instant.now(),
                Instant.now(),
                false
        );
        registry.register(backend);
        RegisteredBackend drained = registry.drain("dyn-drain", "orch-1", 120);
        assertEquals(60, Duration.between(drained.lastHeartbeat(), drained.expiresAt()).getSeconds());
    }

    @Test
    void rejectsHeartbeatFromDifferentOrchestrator() {
        BackendRegistry registry = new BackendRegistry(Set.of(), 30, 5, 60);
        RegisteredBackend backend = new RegisteredBackend(
                "dyn-2",
                "lobby",
                "10.0.0.3",
                9000,
                1,
                100,
                List.of("lobby"),
                "orch-1",
                Instant.now(),
                Instant.now(),
                false
        );
        registry.register(backend);
        assertThrows(IllegalArgumentException.class, () -> registry.heartbeat("dyn-2", "orch-2", null));
    }
}
