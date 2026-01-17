package net.spookly.hyprox.config;

import java.util.List;
import java.util.Map;

public class HyproxConfig {
    public ProxyConfig proxy;
    public AuthConfig auth;
    public RoutingConfig routing;
    public MigrationConfig migration;
    public ObservabilityConfig observability;
    public AgentConfig agent;
    public RegistryConfig registry;

    public static class ProxyConfig {
        public ListenConfig listen;
        public String mode;
        public String defaultPath;
        public List<String> fullProxyPools;
        public QuicConfig quic;
        public TimeoutsConfig timeouts;
        public LimitsConfig limits;
    }

    public static class ListenConfig {
        public String host;
        public Integer port;
    }

    public static class QuicConfig {
        public List<String> alpn;
        /**
         * Optional TLS 1.3 cipher suites to allow for QUIC connections.
         */
        public List<String> cipherSuites;
        public String cert;
        public String key;
        public String clientCa;
        public String backendCa;
        public Boolean requireClientCert;
        public List<String> backendSanAllowlist;
        public Integer maxBidirectionalStreams;
        public Integer maxUnidirectionalStreams;
        public Integer mtu;
    }

    public static class TimeoutsConfig {
        public Integer handshakeMs;
        public Integer idleMs;
    }

    public static class LimitsConfig {
        public Integer handshakesPerMinutePerIp;
        public Integer concurrentPerIp;
    }

    public static class AuthConfig {
        public String mode;
        public ReferralConfig referral;
    }

    public static class ReferralConfig {
        public SigningConfig signing;
        public Integer payloadMaxBytes;
    }

    public static class SigningConfig {
        public String algorithm;
        public String activeKeyId;
        public List<SigningKeyConfig> keys;
        public Integer ttlSeconds;
        public Integer nonceBytes;
    }

    public static class SigningKeyConfig {
        public String keyId;
        public String key;
        public String scope;
        public String scopeId;
        public String validFrom;
        public String validTo;
    }

    public static class RoutingConfig {
        public String defaultPool;
        public List<RuleConfig> rules;
        public Map<String, PoolConfig> pools;
        public HealthConfig health;
    }

    public static class RuleConfig {
        public MatchConfig match;
        public String pool;
    }

    public static class MatchConfig {
        public String clientType;
        public String referralSource;
    }

    public static class PoolConfig {
        public String policy;
        public List<BackendConfig> backends;
    }

    public static class BackendConfig {
        public String id;
        public String host;
        public Integer port;
        public Integer weight;
        public Integer maxPlayers;
        public List<String> tags;
    }

    public static class HealthConfig {
        public Integer intervalSeconds;
        public Integer timeoutMs;
    }

    public static class MigrationConfig {
        public Boolean enabled;
        public String mode;
        public Integer prepareTimeoutMs;
        public Integer cutoverTimeoutMs;
        public Integer bufferMaxPackets;
        public Integer bufferGlobalMaxPackets;
        public Boolean ticketRequired;
        public Integer ticketMaxAgeSeconds;
        public SigningConfig ticketSigning;
        public List<String> allowPools;
    }

    public static class ObservabilityConfig {
        public LoggingConfig logging;
        public TracingConfig tracing;
        public MetricsConfig metrics;
    }

    public static class LoggingConfig {
        public String level;
        public Boolean redactTokens;
    }

    public static class TracingConfig {
        public Boolean enabled;
        public List<Integer> allowlistPacketIds;
        public Boolean memoryOnly;
    }

    public static class MetricsConfig {
        public PrometheusConfig prometheus;
    }

    public static class PrometheusConfig {
        public Boolean enabled;
        public String listen;
    }

    public static class AgentConfig {
        public AgentAuthConfig auth;
        public List<AgentAllowlistEntry> allowlist;
    }

    public static class AgentAuthConfig {
        public String mode;
        public String sharedKey;
        public String clientCa;
    }

    public static class AgentAllowlistEntry {
        public String backendId;
        public String address;
    }

    public static class RegistryConfig {
        public Boolean enabled;
        public String listen;
        public Integer maxRequestBytes;
        public Integer maxListResults;
        public Integer rateLimitPerMinute;
        public List<Integer> allowedPorts;
        public Boolean allowLoopback;
        public Boolean allowPublicAddresses;
        public RegistryAuthConfig auth;
        public List<RegistryAllowlistEntry> allowlist;
        public RegistryDefaults defaults;
        public List<String> allowedNetworks;
    }

    public static class RegistryAuthConfig {
        public String mode;
        public String clientCa;
        public String sharedKey;
        public Integer nonceBytes;
        public Integer clockSkewSeconds;
    }

    public static class RegistryAllowlistEntry {
        public String orchestratorId;
        public String address;
        public List<String> allowedPools;
        public List<String> allowedBackendIdPrefixes;
    }

    public static class RegistryDefaults {
        public Integer ttlSeconds;
        public Integer heartbeatGraceSeconds;
        public Integer drainTimeoutSeconds;
    }
}
