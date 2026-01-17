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

class RegistryRoutingIntegrationTest {
    @Test
    void routesToDynamicBackendWhenRegistered() {
        HyproxConfig config = baseConfig();
        BackendRegistry registry = BackendRegistry.fromConfig(config);
        registry.register(dynamicBackend("dyn-1", "dynamic"));

        RoutingService service = new RoutingService(config, registry, new BackendCapacityTracker(), new BackendHealthTracker());
        RoutingResult result = service.route(new RoutingRequest("game", null, null, null));

        assertNotNull(result.backend());
        assertEquals("dyn-1", result.backend().id());
        assertEquals("selected", result.reason());
    }

    @Test
    void excludesDrainingDynamicBackendFromRouting() {
        HyproxConfig config = baseConfig();
        BackendRegistry registry = BackendRegistry.fromConfig(config);
        registry.register(dynamicBackend("dyn-1", "dynamic"));
        registry.drain("dyn-1", "orch-1", 30);

        RoutingService service = new RoutingService(config, registry, new BackendCapacityTracker(), new BackendHealthTracker());
        RoutingResult result = service.route(new RoutingRequest("game", null, null, null));

        assertNull(result.backend());
        assertEquals("no_backends", result.reason());
    }

    private HyproxConfig baseConfig() {
        HyproxConfig config = new HyproxConfig();
        config.routing = new HyproxConfig.RoutingConfig();
        config.routing.defaultPool = "dynamic";
        config.routing.pools = new LinkedHashMap<>();
        HyproxConfig.PoolConfig pool = new HyproxConfig.PoolConfig();
        pool.policy = "weighted";
        pool.backends = new ArrayList<>();
        config.routing.pools.put("dynamic", pool);
        return config;
    }

    private RegisteredBackend dynamicBackend(String id, String pool) {
        Instant now = Instant.now();
        return new RegisteredBackend(
                id,
                pool,
                "10.0.0.50",
                9000,
                1,
                10,
                List.of("dynamic"),
                "orch-1",
                now,
                now.plusSeconds(30),
                false
        );
    }
}
