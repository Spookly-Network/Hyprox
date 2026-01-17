package net.spookly.hyprox.routing;

import net.spookly.hyprox.config.HyproxConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BackendHealthProbeServiceTest {
    @Test
    void runOnceUpdatesHealthScores() {
        HyproxConfig config = baseConfig();
        BackendHealthTracker tracker = new BackendHealthTracker();
        RoutingService routingService = new RoutingService(config, null, new BackendCapacityTracker(), tracker);
        BackendHealthProbeService service = new BackendHealthProbeService(
                config,
                routingService,
                tracker,
                new StubProbe(Map.of("lobby-1", false, "lobby-2", true))
        );

        service.runOnce();

        BackendTarget failed = routingService.findBackendById("lobby-1", false);
        BackendTarget success = routingService.findBackendById("lobby-2", false);
        assertNotNull(failed);
        assertNotNull(success);
        assertEquals(80, tracker.score(failed));
        assertEquals(100, tracker.score(success));
        service.stop();
    }

    private HyproxConfig baseConfig() {
        HyproxConfig config = new HyproxConfig();
        config.routing = new HyproxConfig.RoutingConfig();
        config.routing.defaultPool = "lobby";
        config.routing.pools = new LinkedHashMap<>();
        config.routing.pools.put("lobby", pool("round_robin", backend("lobby-1"), backend("lobby-2")));
        config.routing.health = new HyproxConfig.HealthConfig();
        config.routing.health.intervalSeconds = 5;
        config.routing.health.timeoutMs = 250;
        return config;
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
        return backend;
    }

    private static final class StubProbe implements BackendHealthProbe {
        private final Map<String, Boolean> outcomes;

        private StubProbe(Map<String, Boolean> outcomes) {
            this.outcomes = outcomes;
        }

        @Override
        public CompletableFuture<Boolean> probe(BackendTarget target, int timeoutMs) {
            Boolean outcome = outcomes.get(target.id());
            return CompletableFuture.completedFuture(Boolean.TRUE.equals(outcome));
        }
    }
}
