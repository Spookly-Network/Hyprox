package net.spookly.hyprox.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigValidator {
    private ConfigValidator() {
    }

    /**
     * Validate configuration, throwing ConfigException on any violations.
     */
    public static void validate(HyproxConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("config is required");
            throwIfErrors(errors);
            return;
        }

        validateProxy(config, errors);
        validateAuth(config, errors);
        validateRouting(config, errors);
        validateMigration(config, errors);
        validateObservability(config, errors);
        validateAgent(config, errors);
        validateRegistry(config, errors);

        throwIfErrors(errors);
    }

    private static void validateProxy(HyproxConfig config, List<String> errors) {
        HyproxConfig.ProxyConfig proxy = config.proxy;
        if (proxy == null) {
            errors.add("proxy section is required");
            return;
        }
        requireNonBlank(errors, proxy.mode, "proxy.mode");
        if (!isOneOf(proxy.mode, "hybrid", "redirect", "full")) {
            errors.add("proxy.mode must be one of: hybrid, redirect, full");
        }
        if (!isBlank(proxy.defaultPath) && !isOneOf(proxy.defaultPath, "redirect", "full")) {
            errors.add("proxy.defaultPath must be one of: redirect, full");
        }

        HyproxConfig.ListenConfig listen = proxy.listen;
        if (listen == null) {
            errors.add("proxy.listen is required");
        } else {
            requireNonBlank(errors, listen.host, "proxy.listen.host");
            requirePort(errors, listen.port, "proxy.listen.port");
        }

        HyproxConfig.QuicConfig quic = proxy.quic;
        if (quic == null) {
            errors.add("proxy.quic is required");
        } else {
            if (quic.alpn == null || quic.alpn.isEmpty()) {
                errors.add("proxy.quic.alpn must include at least one ALPN");
            }
            if (quic.cipherSuites != null) {
                if (quic.cipherSuites.isEmpty()) {
                    errors.add("proxy.quic.cipherSuites must include at least one cipher suite");
                } else {
                    for (String cipher : quic.cipherSuites) {
                        if (isBlank(cipher)) {
                            errors.add("proxy.quic.cipherSuites must not include blank entries");
                        }
                    }
                }
            }
            requireNonBlank(errors, quic.cert, "proxy.quic.cert");
            requireNonBlank(errors, quic.key, "proxy.quic.key");
            if (isTrue(quic.requireClientCert)) {
                requireNonBlank(errors, quic.clientCa, "proxy.quic.clientCa");
            }
            if (usesFullProxy(config)) {
                requireNonBlank(errors, quic.backendCa, "proxy.quic.backendCa");
            }
            if (quic.maxBidirectionalStreams != null && quic.maxBidirectionalStreams <= 0) {
                errors.add("proxy.quic.maxBidirectionalStreams must be greater than 0");
            }
            if (quic.maxUnidirectionalStreams != null && quic.maxUnidirectionalStreams <= 0) {
                errors.add("proxy.quic.maxUnidirectionalStreams must be greater than 0");
            }
        }

        if (proxy.timeouts != null) {
            requirePositive(errors, proxy.timeouts.handshakeMs, "proxy.timeouts.handshakeMs");
            requirePositive(errors, proxy.timeouts.idleMs, "proxy.timeouts.idleMs");
        }
        if (proxy.limits != null) {
            requirePositive(errors, proxy.limits.handshakesPerMinutePerIp, "proxy.limits.handshakesPerMinutePerIp");
            requirePositive(errors, proxy.limits.concurrentPerIp, "proxy.limits.concurrentPerIp");
        }
    }

    private static void validateAuth(HyproxConfig config, List<String> errors) {
        HyproxConfig.AuthConfig auth = config.auth;
        if (auth == null) {
            errors.add("auth section is required");
            return;
        }
        requireNonBlank(errors, auth.mode, "auth.mode");
        if (!isOneOf(auth.mode, "passthrough", "terminate")) {
            errors.add("auth.mode must be one of: passthrough, terminate");
        }

        HyproxConfig.ReferralConfig referral = auth.referral;
        if (referral == null) {
            errors.add("auth.referral is required");
            return;
        }
        if (referral.payloadMaxBytes != null) {
            if (referral.payloadMaxBytes <= 0 || referral.payloadMaxBytes > 4096) {
                errors.add("auth.referral.payloadMaxBytes must be between 1 and 4096");
            }
        }

        HyproxConfig.SigningConfig signing = referral.signing;
        if (signing == null) {
            errors.add("auth.referral.signing is required");
            return;
        }
        requireNonBlank(errors, signing.algorithm, "auth.referral.signing.algorithm");
        if (!isOneOf(signing.algorithm, "hmac-sha256")) {
            errors.add("auth.referral.signing.algorithm must be hmac-sha256");
        }
        requireNonBlank(errors, signing.activeKeyId, "auth.referral.signing.activeKeyId");
        if (signing.keys == null || signing.keys.isEmpty()) {
            errors.add("auth.referral.signing.keys must include at least one key");
            return;
        }
        boolean foundActive = false;
        for (HyproxConfig.SigningKeyConfig key : signing.keys) {
            if (key == null) {
                continue;
            }
            requireNonBlank(errors, key.keyId, "auth.referral.signing.keys.keyId");
            requireNonBlank(errors, key.key, "auth.referral.signing.keys.key");
            requireNonBlank(errors, key.scope, "auth.referral.signing.keys.scope");
            if (!isBlank(key.scope) && !isOneOf(key.scope, "backend", "pool", "global")) {
                errors.add("auth.referral.signing.keys.scope must be backend, pool, or global");
            }
            if (isOneOf(key.scope, "backend", "pool")) {
                requireNonBlank(errors, key.scopeId, "auth.referral.signing.keys.scopeId");
            }
            if (!isBlank(key.keyId) && key.keyId.equals(signing.activeKeyId)) {
                foundActive = true;
            }
        }
        if (!foundActive) {
            errors.add("auth.referral.signing.activeKeyId must match a keyId in signing.keys");
        }
        requirePositive(errors, signing.ttlSeconds, "auth.referral.signing.ttlSeconds");
        requirePositive(errors, signing.nonceBytes, "auth.referral.signing.nonceBytes");
    }

    private static void validateRouting(HyproxConfig config, List<String> errors) {
        HyproxConfig.RoutingConfig routing = config.routing;
        if (routing == null) {
            errors.add("routing section is required");
            return;
        }
        if (routing.pools == null || routing.pools.isEmpty()) {
            errors.add("routing.pools must include at least one pool");
            return;
        }
        requireNonBlank(errors, routing.defaultPool, "routing.defaultPool");
        if (!isBlank(routing.defaultPool) && !routing.pools.containsKey(routing.defaultPool)) {
            errors.add("routing.defaultPool must reference an existing pool");
        }
        if (routing.rules != null) {
            for (HyproxConfig.RuleConfig rule : routing.rules) {
                if (rule == null) {
                    continue;
                }
                requireNonBlank(errors, rule.pool, "routing.rules.pool");
                if (!isBlank(rule.pool) && !routing.pools.containsKey(rule.pool)) {
                    errors.add("routing.rules.pool must reference an existing pool");
                }
            }
        }

        Set<String> backendIds = new HashSet<>();
        for (Map.Entry<String, HyproxConfig.PoolConfig> entry : routing.pools.entrySet()) {
            String poolName = entry.getKey();
            HyproxConfig.PoolConfig pool = entry.getValue();
            if (pool == null) {
                errors.add("routing.pools." + poolName + " is required");
                continue;
            }
            requireNonBlank(errors, pool.policy, "routing.pools." + poolName + ".policy");
            if (!isBlank(pool.policy) && !isOneOf(pool.policy, "weighted", "round_robin")) {
                errors.add("routing.pools." + poolName + ".policy must be weighted or round_robin");
            }
            if (pool.backends == null || pool.backends.isEmpty()) {
                errors.add("routing.pools." + poolName + ".backends must include at least one backend");
                continue;
            }
            for (HyproxConfig.BackendConfig backend : pool.backends) {
                if (backend == null) {
                    continue;
                }
                requireNonBlank(errors, backend.id, "routing.pools." + poolName + ".backends.id");
                requireNonBlank(errors, backend.host, "routing.pools." + poolName + ".backends.host");
                requirePort(errors, backend.port, "routing.pools." + poolName + ".backends.port");
                if (backend.weight != null && backend.weight <= 0) {
                    errors.add("routing.pools." + poolName + ".backends.weight must be greater than 0");
                }
                if (backend.maxPlayers != null && backend.maxPlayers < 0) {
                    errors.add("routing.pools." + poolName + ".backends.maxPlayers must be >= 0");
                }
                if (!isBlank(backend.id)) {
                    if (!backendIds.add(backend.id)) {
                        errors.add("backend id must be unique: " + backend.id);
                    }
                }
            }
        }

        if (config.proxy != null && config.proxy.fullProxyPools != null) {
            for (String poolName : config.proxy.fullProxyPools) {
                if (isBlank(poolName)) {
                    continue;
                }
                if (!routing.pools.containsKey(poolName)) {
                    errors.add("proxy.fullProxyPools must reference an existing pool: " + poolName);
                }
            }
        }

        if (routing.health != null) {
            requirePositive(errors, routing.health.intervalSeconds, "routing.health.intervalSeconds");
            requirePositive(errors, routing.health.timeoutMs, "routing.health.timeoutMs");
        }
    }

    private static void validateMigration(HyproxConfig config, List<String> errors) {
        HyproxConfig.MigrationConfig migration = config.migration;
        if (migration == null) {
            return;
        }
        if (isTrue(migration.enabled)) {
            if (!isTrue(migration.ticketRequired)) {
                errors.add("migration.ticketRequired must be true when migration.enabled is true");
            }
            if (migration.allowPools == null || migration.allowPools.isEmpty()) {
                errors.add("migration.allowPools must be set when migration.enabled is true");
            }
            requirePositive(errors, migration.bufferMaxPackets, "migration.bufferMaxPackets");
            requirePositive(errors, migration.bufferGlobalMaxPackets, "migration.bufferGlobalMaxPackets");
            requirePositive(errors, migration.ticketMaxAgeSeconds, "migration.ticketMaxAgeSeconds");
            validateMigrationTicketSigning(migration, errors);
            HyproxConfig.RoutingConfig routing = config.routing;
            if (routing != null && routing.pools != null && migration.allowPools != null) {
                for (String pool : migration.allowPools) {
                    if (isBlank(pool)) {
                        continue;
                    }
                    if (!routing.pools.containsKey(pool)) {
                        errors.add("migration.allowPools must reference an existing pool: " + pool);
                    }
                }
            }
        }
        if (!isBlank(migration.mode) && !isOneOf(migration.mode, "full_proxy_only")) {
            errors.add("migration.mode must be full_proxy_only");
        }
    }

    private static void validateMigrationTicketSigning(HyproxConfig.MigrationConfig migration, List<String> errors) {
        HyproxConfig.SigningConfig signing = migration.ticketSigning;
        if (signing == null) {
            errors.add("migration.ticketSigning is required when migration.enabled is true");
            return;
        }
        requireNonBlank(errors, signing.algorithm, "migration.ticketSigning.algorithm");
        if (!isOneOf(signing.algorithm, "hmac-sha256")) {
            errors.add("migration.ticketSigning.algorithm must be hmac-sha256");
        }
        requireNonBlank(errors, signing.activeKeyId, "migration.ticketSigning.activeKeyId");
        if (signing.keys == null || signing.keys.isEmpty()) {
            errors.add("migration.ticketSigning.keys must include at least one key");
            return;
        }
        boolean foundActive = false;
        for (HyproxConfig.SigningKeyConfig key : signing.keys) {
            if (key == null) {
                continue;
            }
            requireNonBlank(errors, key.keyId, "migration.ticketSigning.keys.keyId");
            requireNonBlank(errors, key.key, "migration.ticketSigning.keys.key");
            requireNonBlank(errors, key.scope, "migration.ticketSigning.keys.scope");
            if (!isBlank(key.scope) && !isOneOf(key.scope, "backend", "pool", "global")) {
                errors.add("migration.ticketSigning.keys.scope must be backend, pool, or global");
            }
            if (isOneOf(key.scope, "backend", "pool")) {
                requireNonBlank(errors, key.scopeId, "migration.ticketSigning.keys.scopeId");
            }
            if (!isBlank(key.keyId) && key.keyId.equals(signing.activeKeyId)) {
                foundActive = true;
            }
        }
        if (!foundActive) {
            errors.add("migration.ticketSigning.activeKeyId must match a keyId in ticketSigning.keys");
        }
        requirePositive(errors, signing.ttlSeconds, "migration.ticketSigning.ttlSeconds");
        requirePositive(errors, signing.nonceBytes, "migration.ticketSigning.nonceBytes");
        if (migration.ticketMaxAgeSeconds != null && signing.ttlSeconds != null
                && migration.ticketMaxAgeSeconds < signing.ttlSeconds) {
            errors.add("migration.ticketMaxAgeSeconds must be >= migration.ticketSigning.ttlSeconds");
        }
    }

    private static void validateObservability(HyproxConfig config, List<String> errors) {
        HyproxConfig.ObservabilityConfig observability = config.observability;
        if (observability == null || observability.metrics == null) {
            return;
        }
        HyproxConfig.MetricsConfig metrics = observability.metrics;
        if (metrics.prometheus != null && isTrue(metrics.prometheus.enabled)) {
            requireNonBlank(errors, metrics.prometheus.listen, "observability.metrics.prometheus.listen");
        }
    }

    private static void validateAgent(HyproxConfig config, List<String> errors) {
        HyproxConfig.AgentConfig agent = config.agent;
        if (agent == null || agent.auth == null) {
            return;
        }
        HyproxConfig.AgentAuthConfig auth = agent.auth;
        requireNonBlank(errors, auth.mode, "agent.auth.mode");
        if (!isOneOf(auth.mode, "mtls", "hmac")) {
            errors.add("agent.auth.mode must be mtls or hmac");
            return;
        }
        if (isOneOf(auth.mode, "mtls")) {
            requireNonBlank(errors, auth.clientCa, "agent.auth.clientCa");
        }
        if (isOneOf(auth.mode, "hmac")) {
            requireNonBlank(errors, auth.sharedKey, "agent.auth.sharedKey");
        }
    }

    private static void validateRegistry(HyproxConfig config, List<String> errors) {
        HyproxConfig.RegistryConfig registry = config.registry;
        if (registry == null || !isTrue(registry.enabled)) {
            return;
        }
        requireNonBlank(errors, registry.listen, "registry.listen");
        if (registry.allowedNetworks == null || registry.allowedNetworks.isEmpty()) {
            errors.add("registry.allowedNetworks must include at least one CIDR");
        }
        if (registry.maxRequestBytes != null && registry.maxRequestBytes <= 0) {
            errors.add("registry.maxRequestBytes must be greater than 0");
        }
        if (registry.maxListResults != null && registry.maxListResults <= 0) {
            errors.add("registry.maxListResults must be greater than 0");
        }
        if (registry.rateLimitPerMinute != null && registry.rateLimitPerMinute <= 0) {
            errors.add("registry.rateLimitPerMinute must be greater than 0");
        }
        if (registry.allowedPorts == null || registry.allowedPorts.isEmpty()) {
            errors.add("registry.allowedPorts must include at least one port");
        } else {
            for (Integer port : registry.allowedPorts) {
                requirePort(errors, port, "registry.allowedPorts");
            }
        }
        if (registry.allowlist == null || registry.allowlist.isEmpty()) {
            errors.add("registry.allowlist must include at least one orchestrator");
        }
        HyproxConfig.RegistryAuthConfig auth = registry.auth;
        if (auth == null) {
            errors.add("registry.auth is required when registry.enabled is true");
        } else {
            requireNonBlank(errors, auth.mode, "registry.auth.mode");
            if (!isOneOf(auth.mode, "mtls", "hmac")) {
                errors.add("registry.auth.mode must be mtls or hmac");
            } else if (isOneOf(auth.mode, "mtls")) {
                requireNonBlank(errors, auth.clientCa, "registry.auth.clientCa");
            } else if (isOneOf(auth.mode, "hmac")) {
                requireNonBlank(errors, auth.sharedKey, "registry.auth.sharedKey");
                requirePositive(errors, auth.nonceBytes, "registry.auth.nonceBytes");
                requirePositive(errors, auth.clockSkewSeconds, "registry.auth.clockSkewSeconds");
            }
        }
        if (registry.allowlist != null) {
            for (HyproxConfig.RegistryAllowlistEntry entry : registry.allowlist) {
                if (entry == null) {
                    continue;
                }
                requireNonBlank(errors, entry.orchestratorId, "registry.allowlist.orchestratorId");
                requireNonBlank(errors, entry.address, "registry.allowlist.address");
                if (entry.allowedPools == null || entry.allowedPools.isEmpty()) {
                    errors.add("registry.allowlist.allowedPools must include at least one pool");
                } else if (config.routing != null && config.routing.pools != null) {
                    for (String pool : entry.allowedPools) {
                        if (isBlank(pool)) {
                            continue;
                        }
                        if (!config.routing.pools.containsKey(pool)) {
                            errors.add("registry.allowlist.allowedPools must reference an existing pool: " + pool);
                        }
                    }
                }
                if (entry.allowedBackendIdPrefixes == null || entry.allowedBackendIdPrefixes.isEmpty()) {
                    errors.add("registry.allowlist.allowedBackendIdPrefixes must include at least one prefix");
                }
            }
        }
        if (registry.defaults == null) {
            errors.add("registry.defaults is required when registry.enabled is true");
        } else {
            requirePositive(errors, registry.defaults.ttlSeconds, "registry.defaults.ttlSeconds");
            requirePositive(errors, registry.defaults.heartbeatGraceSeconds, "registry.defaults.heartbeatGraceSeconds");
            requirePositive(errors, registry.defaults.drainTimeoutSeconds, "registry.defaults.drainTimeoutSeconds");
        }
    }

    private static boolean usesFullProxy(HyproxConfig config) {
        if (config == null || config.proxy == null) {
            return false;
        }
        if (isOneOf(config.proxy.mode, "full")) {
            return true;
        }
        if (isOneOf(config.proxy.defaultPath, "full")) {
            return true;
        }
        if (config.proxy.fullProxyPools != null && !config.proxy.fullProxyPools.isEmpty()) {
            return true;
        }
        return config.migration != null && isTrue(config.migration.enabled);
    }

    private static void requireNonBlank(List<String> errors, String value, String field) {
        if (isBlank(value)) {
            errors.add(field + " is required");
        }
    }

    private static void requirePort(List<String> errors, Integer value, String field) {
        if (value == null || value < 1 || value > 65535) {
            errors.add(field + " must be between 1 and 65535");
        }
    }

    private static void requirePositive(List<String> errors, Integer value, String field) {
        if (value == null || value <= 0) {
            errors.add(field + " must be greater than 0");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isTrue(Boolean value) {
        return value != null && value;
    }

    private static boolean isOneOf(String value, String... options) {
        if (value == null) {
            return false;
        }
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    private static void throwIfErrors(List<String> errors) {
        if (!errors.isEmpty()) {
            StringBuilder builder = new StringBuilder("Invalid config:\n");
            for (String error : errors) {
                builder.append("- ").append(error).append('\n');
            }
            throw new ConfigException(builder.toString());
        }
    }
}
