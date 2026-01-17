package net.spookly.hyprox.routing;

import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.registry.BackendRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoutingPlannerTest {
    @Test
    void combinesRoutingAndPathDecision() {
        HyproxConfig config = new HyproxConfig();
        config.proxy = proxy("hybrid", "redirect", List.of("game"));
        config.routing = new HyproxConfig.RoutingConfig();
        config.routing.defaultPool = "game";
        config.routing.pools = new LinkedHashMap<>();
        config.routing.pools.put("game", pool("weighted", backend("game-1")));

        RoutingService routingService = new RoutingService(
                config,
                BackendRegistry.fromConfig(config),
                new BackendCapacityTracker(),
                new BackendHealthTracker()
        );
        RoutingPlanner planner = new RoutingPlanner(routingService, new PathSelector(config));

        RoutingDecision decision = planner.decide(new RoutingRequest("game", null, null, null));

        assertEquals("game", decision.pool());
        assertEquals(DataPath.FULL_PROXY, decision.dataPath());
        assertNotNull(decision.backend());
    }

    private HyproxConfig.ProxyConfig proxy(String mode, String defaultPath, List<String> fullProxyPools) {
        HyproxConfig.ProxyConfig proxy = new HyproxConfig.ProxyConfig();
        proxy.mode = mode;
        proxy.defaultPath = defaultPath;
        proxy.fullProxyPools = fullProxyPools;
        return proxy;
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
}
