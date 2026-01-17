package net.spookly.hyprox.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.BackendCapacityTracker;
import net.spookly.hyprox.routing.BackendHealthTracker;
import net.spookly.hyprox.routing.RoutingService;
import org.junit.jupiter.api.Test;

class MigrationIntegrationTest {
    @Test
    void failsMigrationWhenBufferOverflowSignalsRollback() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        MigrationTicketService ticketService = new MigrationTicketService(
                config,
                routingService,
                fixedClock(),
                bytes -> MigrationTestNonce.fixed(bytes)
        );
        MigrationStateMachine stateMachine = new MigrationStateMachine(config, fixedClock());
        MigrationBufferManager bufferManager = new MigrationBufferManager(config);

        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000000031");
        MigrationContext context = new MigrationContext(clientId, "source-1", "target-1");

        MigrationTicketService.SignResult signed = ticketService.signTicket("source-1", "target-1", clientId);
        assertTrue(signed.ok());
        assertNotNull(signed.payload());

        MigrationTicketService.VerifyResult verified =
                ticketService.verifyTicket(signed.payload(), clientId, "source-1", "target-1");
        assertTrue(verified.ok());
        assertNotNull(verified.ticket());

        MigrationStateMachine.TransitionResult start = stateMachine.start(context, verified);
        assertTrue(start.ok());
        assertEquals(MigrationPhase.PREPARE, stateMachine.phase());

        MigrationBuffer<String> buffer = bufferManager.createBuffer(context);
        assertTrue(buffer.add("first").ok());
        assertEquals(1, bufferManager.globalBufferedCount());

        MigrationBuffer.BufferResult overflow = buffer.add("second");
        assertFalse(overflow.ok());
        assertTrue(overflow.rollback());
        assertEquals("migration buffer limit exceeded", overflow.error());

        MigrationStateMachine.TransitionResult failed = stateMachine.fail(overflow.error());
        assertFalse(failed.ok());
        assertEquals(MigrationPhase.FAILED, stateMachine.phase());
        assertEquals("migration buffer limit exceeded", stateMachine.failureReason());

        buffer.close();
        assertEquals(0, bufferManager.globalBufferedCount());

        MigrationStateMachine.TransitionResult reset = stateMachine.reset();
        assertTrue(reset.ok());
        assertEquals(MigrationPhase.IDLE, stateMachine.phase());
    }

    private HyproxConfig buildConfig() {
        HyproxConfig config = new HyproxConfig();
        config.migration = new HyproxConfig.MigrationConfig();
        config.migration.enabled = true;
        config.migration.ticketRequired = true;
        config.migration.bufferMaxPackets = 1;
        config.migration.bufferGlobalMaxPackets = 2;
        config.migration.ticketMaxAgeSeconds = 30;
        config.migration.ticketSigning = new HyproxConfig.SigningConfig();
        config.migration.ticketSigning.algorithm = "hmac-sha256";
        config.migration.ticketSigning.activeKeyId = "k1";
        config.migration.ticketSigning.ttlSeconds = 30;
        config.migration.ticketSigning.nonceBytes = 8;

        HyproxConfig.SigningKeyConfig key = new HyproxConfig.SigningKeyConfig();
        key.keyId = "k1";
        key.key = "secret";
        key.scope = "global";
        key.validFrom = "2025-01-01T00:00:00Z";
        key.validTo = "2025-12-31T23:59:59Z";
        config.migration.ticketSigning.keys = List.of(key);

        config.routing = new HyproxConfig.RoutingConfig();
        config.routing.defaultPool = "lobby";
        config.routing.pools = new LinkedHashMap<>();
        config.routing.pools.put("lobby", pool("weighted", backend("source-1"), backend("target-1")));
        return config;
    }

    private HyproxConfig.PoolConfig pool(String policy, HyproxConfig.BackendConfig... backends) {
        HyproxConfig.PoolConfig pool = new HyproxConfig.PoolConfig();
        pool.policy = policy;
        pool.backends = List.of(backends);
        return pool;
    }

    private HyproxConfig.BackendConfig backend(String id) {
        HyproxConfig.BackendConfig backend = new HyproxConfig.BackendConfig();
        backend.id = id;
        backend.host = "10.0.0.1";
        backend.port = 9000;
        backend.weight = 1;
        backend.maxPlayers = 150;
        backend.tags = List.of("static");
        return backend;
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static final class MigrationTestNonce {
        private static String fixed(int bytes) {
            byte[] buffer = new byte[Math.max(bytes, 1)];
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        }
    }
}
