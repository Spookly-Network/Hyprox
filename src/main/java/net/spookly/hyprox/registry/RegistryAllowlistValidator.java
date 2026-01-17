package net.spookly.hyprox.registry;

import java.net.InetAddress;
import java.util.Objects;

import net.spookly.hyprox.config.HyproxConfig;

/**
 * Validates registry allowlist rules for orchestrators and backend identities.
 */
final class RegistryAllowlistValidator {
    /**
     * Configuration used to enforce allowlist policies.
     */
    private final HyproxConfig config;

    RegistryAllowlistValidator(HyproxConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    void validateOrchestrator(String orchestratorId, InetAddress address) {
        requireNonBlank(orchestratorId, "orchestratorId");
        if (config.registry == null || config.registry.allowlist == null) {
            throw new IllegalArgumentException("registry allowlist missing");
        }
        boolean matched = false;
        for (HyproxConfig.RegistryAllowlistEntry entry : config.registry.allowlist) {
            if (entry == null) {
                continue;
            }
            if (!orchestratorId.equals(entry.orchestratorId)) {
                continue;
            }
            if (entry.address == null || entry.address.equals(address.getHostAddress())) {
                matched = true;
            }
        }
        if (!matched) {
            throw new IllegalArgumentException("orchestrator not allowlisted");
        }
    }

    void validatePool(String pool, String orchestratorId) {
        requireNonBlank(pool, "pool");
        if (config.routing == null || config.routing.pools == null || !config.routing.pools.containsKey(pool)) {
            throw new IllegalArgumentException("pool not found: " + pool);
        }
        if (config.registry == null || config.registry.allowlist == null) {
            throw new IllegalArgumentException("registry allowlist missing");
        }
        boolean allowed = false;
        for (HyproxConfig.RegistryAllowlistEntry entry : config.registry.allowlist) {
            if (entry == null) {
                continue;
            }
            if (!orchestratorId.equals(entry.orchestratorId)) {
                continue;
            }
            if (entry.allowedPools != null && entry.allowedPools.contains(pool)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IllegalArgumentException("pool not allowed for orchestrator");
        }
    }

    void validateBackendId(String backendId, String orchestratorId) {
        requireNonBlank(backendId, "backendId");
        if (config.registry == null || config.registry.allowlist == null) {
            throw new IllegalArgumentException("registry allowlist missing");
        }
        for (HyproxConfig.RegistryAllowlistEntry entry : config.registry.allowlist) {
            if (entry == null) {
                continue;
            }
            if (!orchestratorId.equals(entry.orchestratorId)) {
                continue;
            }
            if (entry.allowedBackendIdPrefixes != null) {
                for (String prefix : entry.allowedBackendIdPrefixes) {
                    if (backendId.startsWith(prefix)) {
                        return;
                    }
                }
            }
        }
        throw new IllegalArgumentException("backendId not allowed by orchestrator prefix rules");
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
