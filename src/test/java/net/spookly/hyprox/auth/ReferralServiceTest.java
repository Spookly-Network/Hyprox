package net.spookly.hyprox.auth;

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
import net.spookly.hyprox.routing.BackendTarget;
import net.spookly.hyprox.routing.RoutingService;
import org.junit.jupiter.api.Test;

class ReferralServiceTest {
    @Test
    void signsAndVerifiesReferral() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        ReferralService service = new ReferralService(
                config,
                routingService,
                fixedClock(),
                bytes -> ReferralTestNonce.fixed(bytes)
        );

        BackendTarget target = routingService.findBackendById("lobby-1", false);
        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ReferralService.SignResult signed = service.signReferral(target, clientId);

        assertTrue(signed.ok());
        assertNotNull(signed.payload());

        ReferralService.VerifyResult verified = service.verifyReferral(signed.payload(), clientId);
        assertTrue(verified.ok());
        assertEquals("lobby-1", verified.targetBackendId());
    }

    @Test
    void rejectsReplayedNonce() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        ReferralService service = new ReferralService(
                config,
                routingService,
                fixedClock(),
                bytes -> ReferralTestNonce.fixed(bytes)
        );

        BackendTarget target = routingService.findBackendById("lobby-1", false);
        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ReferralService.SignResult signed = service.signReferral(target, clientId);

        ReferralService.VerifyResult first = service.verifyReferral(signed.payload(), clientId);
        ReferralService.VerifyResult second = service.verifyReferral(signed.payload(), clientId);

        assertTrue(first.ok());
        assertFalse(second.ok());
        assertEquals("referral nonce replayed", second.error());
    }

    @Test
    void rejectsClientUuidMismatch() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        ReferralService service = new ReferralService(
                config,
                routingService,
                fixedClock(),
                bytes -> ReferralTestNonce.fixed(bytes)
        );

        BackendTarget target = routingService.findBackendById("lobby-1", false);
        ReferralService.SignResult signed = service.signReferral(
                target,
                UUID.fromString("00000000-0000-0000-0000-000000000003")
        );

        ReferralService.VerifyResult verified = service.verifyReferral(
                signed.payload(),
                UUID.fromString("00000000-0000-0000-0000-000000000004")
        );

        assertFalse(verified.ok());
        assertEquals("referral payload client uuid mismatch", verified.error());
    }

    private HyproxConfig buildConfig() {
        HyproxConfig config = new HyproxConfig();
        config.auth = new HyproxConfig.AuthConfig();
        config.auth.mode = "passthrough";
        config.auth.referral = new HyproxConfig.ReferralConfig();
        config.auth.referral.payloadMaxBytes = 4096;
        config.auth.referral.signing = new HyproxConfig.SigningConfig();
        config.auth.referral.signing.algorithm = "hmac-sha256";
        config.auth.referral.signing.activeKeyId = "k1";
        config.auth.referral.signing.ttlSeconds = 30;
        config.auth.referral.signing.nonceBytes = 8;

        HyproxConfig.SigningKeyConfig key = new HyproxConfig.SigningKeyConfig();
        key.keyId = "k1";
        key.key = "secret";
        key.scope = "global";
        key.validFrom = "2025-01-01T00:00:00Z";
        key.validTo = "2025-12-31T23:59:59Z";
        config.auth.referral.signing.keys = List.of(key);

        config.routing = new HyproxConfig.RoutingConfig();
        config.routing.defaultPool = "lobby";
        config.routing.pools = new LinkedHashMap<>();
        config.routing.pools.put("lobby", pool("weighted", backend("lobby-1")));
        return config;
    }

    private HyproxConfig.PoolConfig pool(String policy, HyproxConfig.BackendConfig backend) {
        HyproxConfig.PoolConfig pool = new HyproxConfig.PoolConfig();
        pool.policy = policy;
        pool.backends = List.of(backend);
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

    private static final class ReferralTestNonce {
        private static String fixed(int bytes) {
            byte[] buffer = new byte[Math.max(bytes, 1)];
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        }
    }
}
