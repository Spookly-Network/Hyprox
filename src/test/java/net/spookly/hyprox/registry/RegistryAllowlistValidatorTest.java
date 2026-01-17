package net.spookly.hyprox.registry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.spookly.hyprox.config.HyproxConfig;
import org.junit.jupiter.api.Test;

class RegistryAllowlistValidatorTest {
    @Test
    void rejectsOrchestratorWhenAddressNotAllowlisted() throws Exception {
        RegistryAllowlistValidator validator = new RegistryAllowlistValidator(config());
        InetAddress address = InetAddress.getByName("10.0.0.6");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateOrchestrator("orch-1", address)
        );
        assertEquals("orchestrator not allowlisted", error.getMessage());
    }

    @Test
    void rejectsPoolNotAllowedForOrchestrator() {
        RegistryAllowlistValidator validator = new RegistryAllowlistValidator(config());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validatePool("arena", "orch-1")
        );
        assertEquals("pool not allowed for orchestrator", error.getMessage());
    }

    @Test
    void rejectsBackendIdOutsidePrefix() {
        RegistryAllowlistValidator validator = new RegistryAllowlistValidator(config());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateBackendId("static-1", "orch-1")
        );
        assertEquals("backendId not allowed by orchestrator prefix rules", error.getMessage());
    }

    @Test
    void acceptsAllowlistedOrchestratorPoolAndBackendId() throws Exception {
        RegistryAllowlistValidator validator = new RegistryAllowlistValidator(config());
        InetAddress address = InetAddress.getByName("10.0.0.5");

        assertDoesNotThrow(() -> validator.validateOrchestrator("orch-1", address));
        assertDoesNotThrow(() -> validator.validatePool("lobby", "orch-1"));
        assertDoesNotThrow(() -> validator.validateBackendId("dyn-123", "orch-1"));
    }

    private HyproxConfig config() {
        HyproxConfig config = new HyproxConfig();
        config.registry = new HyproxConfig.RegistryConfig();
        config.registry.allowlist = List.of(allowlistEntry());

        HyproxConfig.RoutingConfig routing = new HyproxConfig.RoutingConfig();
        Map<String, HyproxConfig.PoolConfig> pools = new HashMap<>();
        pools.put("lobby", new HyproxConfig.PoolConfig());
        pools.put("arena", new HyproxConfig.PoolConfig());
        routing.pools = pools;
        config.routing = routing;
        return config;
    }

    private HyproxConfig.RegistryAllowlistEntry allowlistEntry() {
        HyproxConfig.RegistryAllowlistEntry entry = new HyproxConfig.RegistryAllowlistEntry();
        entry.orchestratorId = "orch-1";
        entry.address = "10.0.0.5";
        entry.allowedPools = List.of("lobby");
        entry.allowedBackendIdPrefixes = List.of("dyn-");
        return entry;
    }
}
