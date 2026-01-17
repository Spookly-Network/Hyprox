package net.spookly.hyprox.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.registry.BackendRegistry;
import net.spookly.hyprox.registry.RegisteredBackend;
import org.junit.jupiter.api.Test;

class RoutingServiceTest {
    @Test
    void routesByRuleMatch() {
        HyproxConfig config = baseConfig();
        config.routing.rules = List.of(rule("editor", null, "edit"));
        config.routing.pools.put("edit", pool("weighted", backend("edit-1")));

        RoutingService service = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        RoutingResult result = service.route(new RoutingRequest("editor", null, null, null));

        assertEquals("edit", result.pool());
        assertNotNull(result.backend());
        assertEquals("edit-1", result.backend().id());
    }

    @Test
    void defaultsWhenNoRuleMatches() {
        HyproxConfig config = baseConfig();
        config.routing.rules = List.of(rule("editor", null, "edit"));
        config.routing.pools.put("edit", pool("weighted", backend("edit-1")));

        RoutingService service = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        RoutingResult result = service.route(new RoutingRequest("game", null, null, null));

        assertEquals("lobby", result.pool());
        assertNotNull(result.backend());
        assertEquals("lobby-1", result.backend().id());
    }

    @Test
    void roundRobinCycles() {
        HyproxConfig config = baseConfig();
        config.routing.pools.put("lobby", pool("round_robin", backend("lobby-1"), backend("lobby-2")));

        RoutingService service = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        RoutingResult first = service.route(new RoutingRequest("game", null, null, null));
        RoutingResult second = service.route(new RoutingRequest("game", null, null, null));
        RoutingResult third = service.route(new RoutingRequest("game", null, null, null));

        assertEquals("lobby-1", first.backend().id());
        assertEquals("lobby-2", second.backend().id());
        assertEquals("lobby-1", third.backend().id());
    }

    @Test
    void drainingDynamicBackendsExcludedByDefault() {
        HyproxConfig config = baseConfig();
        BackendRegistry registry = BackendRegistry.fromConfig(config);
        RoutingService service = new RoutingService(config, registry, new BackendCapacityTracker(), new BackendHealthTracker());

        RegisteredBackend backend = new RegisteredBackend(
                "dyn-1",
                "lobby",
                "10.0.0.50",
                9000,
                1,
                10,
                List.of("dynamic"),
                "orch-1",
                Instant.now(),
                Instant.now().plusSeconds(30),
                false
        );
        registry.register(backend);
        registry.drain("dyn-1", "orch-1", 30);

        List<BackendTarget> candidates = service.listBackends("lobby", false);
        assertEquals(1, candidates.size());
        assertEquals("lobby-1", candidates.get(0).id());
    }

    @Test
    void rejectsWhenPoolAtCapacity() {
        HyproxConfig config = baseConfig();
        HyproxConfig.BackendConfig backend = backend("lobby-1");
        backend.maxPlayers = 1;
        config.routing.pools.put("lobby", pool("round_robin", backend));

        BackendCapacityTracker tracker = new BackendCapacityTracker();
        RoutingService service = new RoutingService(config, null, tracker, new BackendHealthTracker());

        RoutingResult first = service.route(new RoutingRequest("game", null, null, null));
        RoutingResult second = service.route(new RoutingRequest("game", null, null, null));

        assertNotNull(first.backend());
        assertNotNull(first.reservation());
        assertNull(second.backend());
        assertEquals("pool_full", second.reason());

        first.reservation().release();
        RoutingResult third = service.route(new RoutingRequest("game", null, null, null));
        assertNotNull(third.backend());
        third.reservation().release();
    }

    @Test
    void avoidsUnhealthyBackendsWhenPossible() {
        HyproxConfig config = baseConfig();
        config.routing.pools.put("lobby", pool("round_robin", backend("lobby-1"), backend("lobby-2")));
        BackendHealthTracker healthTracker = new BackendHealthTracker();
        BackendTarget unhealthy = backendTarget("lobby-1");
        healthTracker.recordPassiveFailure(unhealthy);
        healthTracker.recordPassiveFailure(unhealthy);
        healthTracker.recordPassiveFailure(unhealthy);

        RoutingService service = new RoutingService(config, null, new BackendCapacityTracker(), healthTracker);
        RoutingResult result = service.route(new RoutingRequest("game", null, null, null));

        assertNotNull(result.backend());
        assertEquals("lobby-2", result.backend().id());
    }

    @Test
    void consistentSelectionStaysStableWhenWeightIncreases() {
        HyproxConfig config = baseConfig();
        config.routing.pools.put("lobby", pool("weighted", backend("lobby-1"), backend("lobby-2")));

        RoutingService service = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        String selectionKey = "client-42";
        RoutingResult initial = service.route(new RoutingRequest("game", null, selectionKey, null));

        assertNotNull(initial.backend());
        String selectedId = initial.backend().id();

        for (HyproxConfig.BackendConfig backend : config.routing.pools.get("lobby").backends) {
            if (selectedId.equals(backend.id)) {
                backend.weight = 5;
                break;
            }
        }

        RoutingService updated = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        RoutingResult afterChange = updated.route(new RoutingRequest("game", null, selectionKey, null));

        assertNotNull(afterChange.backend());
        assertEquals(selectedId, afterChange.backend().id());
    }

    @Test
    void routesToExplicitReferralTarget() {
        HyproxConfig config = baseConfig();
        config.routing.pools.put("lobby", pool("weighted", backend("lobby-1"), backend("lobby-2")));

        RoutingService service = new RoutingService(config, null, new BackendCapacityTracker(), new BackendHealthTracker());
        RoutingResult result = service.route(new RoutingRequest("game", null, null, "lobby-2"));

        assertNotNull(result.backend());
        assertEquals("lobby-2", result.backend().id());
    }

    private HyproxConfig baseConfig() {
        HyproxConfig config = new HyproxConfig();
        config.routing = new HyproxConfig.RoutingConfig();
        config.routing.defaultPool = "lobby";
        config.routing.pools = new LinkedHashMap<>();
        config.routing.pools.put("lobby", pool("round_robin", backend("lobby-1")));
        return config;
    }

    private HyproxConfig.RuleConfig rule(String clientType, String referralSource, String pool) {
        HyproxConfig.RuleConfig rule = new HyproxConfig.RuleConfig();
        rule.pool = pool;
        rule.match = new HyproxConfig.MatchConfig();
        rule.match.clientType = clientType;
        rule.match.referralSource = referralSource;
        return rule;
    }

    private HyproxConfig.PoolConfig pool(String policy, HyproxConfig.BackendConfig... backends) {
        HyproxConfig.PoolConfig pool = new HyproxConfig.PoolConfig();
        pool.policy = policy;
        pool.backends = new ArrayList<>();
        for (HyproxConfig.BackendConfig backend : backends) {
            pool.backends.add(backend);
        }
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

    private BackendTarget backendTarget(String id) {
        return new BackendTarget(
                id,
                "lobby",
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
