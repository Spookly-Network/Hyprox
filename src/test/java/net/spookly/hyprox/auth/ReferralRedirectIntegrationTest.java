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
import net.spookly.hyprox.routing.DataPath;
import net.spookly.hyprox.routing.PathSelector;
import net.spookly.hyprox.routing.RoutingDecision;
import net.spookly.hyprox.routing.RoutingPlanner;
import net.spookly.hyprox.routing.RoutingRequest;
import net.spookly.hyprox.routing.RoutingService;
import org.junit.jupiter.api.Test;

class ReferralRedirectIntegrationTest {
    @Test
    void routesReferralTargetThroughRedirectPath() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        ReferralService referralService = new ReferralService(
                config,
                routingService,
                fixedClock(),
                bytes -> ReferralTestNonce.fixed(bytes)
        );
        RoutingPlanner planner = new RoutingPlanner(routingService, new PathSelector(config));

        BackendTarget target = routingService.findBackendById("lobby-2", false);
        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        ReferralService.SignResult signed = referralService.signReferral(target, clientId);

        assertTrue(signed.ok());
        ReferralService.VerifyResult verified = referralService.verifyReferral(signed.payload(), clientId);

        assertTrue(verified.ok());
        RoutingDecision decision = planner.decide(new RoutingRequest("game", null, null, verified.targetBackendId()));

        assertEquals(DataPath.REDIRECT, decision.dataPath());
        assertEquals("lobby", decision.pool());
        assertNotNull(decision.backend());
        assertEquals("lobby-2", decision.backend().id());
        assertEquals("referral_target", decision.reason());
        assertNotNull(decision.reservation());
        decision.reservation().release();
    }

    @Test
    void fallsBackToDefaultSelectionWhenReferralIsInvalid() {
        HyproxConfig config = buildConfig();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        ReferralService referralService = new ReferralService(
                config,
                routingService,
                fixedClock(),
                bytes -> ReferralTestNonce.fixed(bytes)
        );
        RoutingPlanner planner = new RoutingPlanner(routingService, new PathSelector(config));

        BackendTarget target = routingService.findBackendById("lobby-2", false);
        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        ReferralService.SignResult signed = referralService.signReferral(target, clientId);

        ReferralService.VerifyResult verified = referralService.verifyReferral(
                signed.payload(),
                UUID.fromString("00000000-0000-0000-0000-000000000022")
        );

        assertFalse(verified.ok());
        RoutingDecision decision = planner.decide(new RoutingRequest("game", null, null, verified.targetBackendId()));

        assertEquals(DataPath.REDIRECT, decision.dataPath());
        assertEquals("lobby", decision.pool());
        assertNotNull(decision.backend());
        assertEquals("lobby-1", decision.backend().id());
        assertEquals("selected", decision.reason());
        assertNotNull(decision.reservation());
        decision.reservation().release();
    }

    private HyproxConfig buildConfig() {
        HyproxConfig config = new HyproxConfig();
        config.proxy = new HyproxConfig.ProxyConfig();
        config.proxy.mode = "redirect";
        config.proxy.defaultPath = "redirect";

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
        key.scope = "backend";
        key.scopeId = "lobby-2";
        key.validFrom = "2025-01-01T00:00:00Z";
        key.validTo = "2025-12-31T23:59:59Z";
        config.auth.referral.signing.keys = List.of(key);

        config.routing = new HyproxConfig.RoutingConfig();
        config.routing.defaultPool = "lobby";
        config.routing.pools = new LinkedHashMap<>();
        config.routing.pools.put("lobby", pool("round_robin", backend("lobby-1"), backend("lobby-2")));
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

    private static final class ReferralTestNonce {
        private static String fixed(int bytes) {
            byte[] buffer = new byte[Math.max(bytes, 1)];
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        }
    }
}
