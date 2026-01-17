package net.spookly.hyprox.migration;

import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.BackendCapacityTracker;
import net.spookly.hyprox.routing.BackendHealthTracker;
import net.spookly.hyprox.routing.RoutingService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationTicketServiceTest {
    @Test
    void signsAndVerifiesTicket() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        MigrationTicketService service = new MigrationTicketService(
                config,
                routingService,
                fixedClock(),
                bytes -> TicketTestNonce.fixed(bytes)
        );

        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        MigrationTicketService.SignResult signed = service.signTicket("game-1", "game-2", clientId);

        assertTrue(signed.ok());
        assertNotNull(signed.payload());

        MigrationTicketService.VerifyResult verified = service.verifyTicket(signed.payload(), clientId, "game-1", "game-2");
        assertTrue(verified.ok());
        assertEquals("game-2", verified.ticket().targetBackendId());
    }

    @Test
    void rejectsReplayedNonce() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        MigrationTicketService service = new MigrationTicketService(
                config,
                routingService,
                fixedClock(),
                bytes -> TicketTestNonce.fixed(bytes)
        );

        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        MigrationTicketService.SignResult signed = service.signTicket("game-1", "game-2", clientId);

        MigrationTicketService.VerifyResult first = service.verifyTicket(signed.payload(), clientId, "game-1", "game-2");
        MigrationTicketService.VerifyResult second = service.verifyTicket(signed.payload(), clientId, "game-1", "game-2");

        assertTrue(first.ok());
        assertFalse(second.ok());
        assertEquals("migration ticket nonce replayed", second.error());
    }

    @Test
    void rejectsClientUuidMismatch() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        MigrationTicketService service = new MigrationTicketService(
                config,
                routingService,
                fixedClock(),
                bytes -> TicketTestNonce.fixed(bytes)
        );

        MigrationTicketService.SignResult signed = service.signTicket(
                "game-1",
                "game-2",
                UUID.fromString("00000000-0000-0000-0000-000000000012")
        );

        MigrationTicketService.VerifyResult verified = service.verifyTicket(
                signed.payload(),
                UUID.fromString("00000000-0000-0000-0000-000000000013"),
                "game-1",
                "game-2"
        );

        assertFalse(verified.ok());
        assertEquals("migration ticket client uuid mismatch", verified.error());
    }

    private HyproxConfig buildConfig() {
        HyproxConfig config = new HyproxConfig();
        config.migration = new HyproxConfig.MigrationConfig();
        config.migration.enabled = true;
        config.migration.ticketRequired = true;
        config.migration.ticketMaxAgeSeconds = 30;
        config.migration.allowPools = List.of("game");
        config.migration.ticketSigning = new HyproxConfig.SigningConfig();
        config.migration.ticketSigning.algorithm = "hmac-sha256";
        config.migration.ticketSigning.activeKeyId = "k1";
        config.migration.ticketSigning.ttlSeconds = 30;
        config.migration.ticketSigning.nonceBytes = 8;

        HyproxConfig.SigningKeyConfig key = new HyproxConfig.SigningKeyConfig();
        key.keyId = "k1";
        key.key = "secret";
        key.scope = "backend";
        key.scopeId = "game-1";
        key.validFrom = "2025-01-01T00:00:00Z";
        key.validTo = "2025-12-31T23:59:59Z";
        config.migration.ticketSigning.keys = List.of(key);

        config.routing = new HyproxConfig.RoutingConfig();
        config.routing.defaultPool = "game";
        config.routing.pools = new LinkedHashMap<>();
        config.routing.pools.put("game", pool("weighted", backend("game-1"), backend("game-2")));
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
        return backend;
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static final class TicketTestNonce {
        private static String fixed(int bytes) {
            byte[] buffer = new byte[Math.max(bytes, 1)];
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        }
    }
}
