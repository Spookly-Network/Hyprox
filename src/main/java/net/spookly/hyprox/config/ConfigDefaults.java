package net.spookly.hyprox.config;

/**
 * Default configuration template written when no config file exists.
 */
public final class ConfigDefaults {
    private static final String DEFAULT_YAML_TEMPLATE = """
            # Generated default Hyprox config.
            # A referral HMAC is stored at %s. Keep it secret.
            proxy:
              listen:
                host: 0.0.0.0
                port: 20000
              mode: redirect
              defaultPath: redirect
              quic:
                alpn: ["hytale/1"]
                cert: certs/proxy.crt
                key: certs/proxy.key
                maxBidirectionalStreams: 100
                maxUnidirectionalStreams: 100
                mtu: 1350
              timeouts:
                handshakeMs: 10000
                idleMs: 30000
              limits:
                handshakesPerMinutePerIp: 60
                concurrentPerIp: 4

            auth:
              mode: passthrough
              referral:
                signing:
                  algorithm: hmac-sha256
                  activeKeyId: default
                  keys:
                    - keyId: default
                      key: path:%s
                      scope: global
                  ttlSeconds: 60
                  nonceBytes: 16
                payloadMaxBytes: 4096

            routing:
              defaultPool: lobby
              pools:
                lobby:
                  policy: weighted
                  backends:
                    - id: lobby-1
                      host: 127.0.0.1
                      port: 20001
                      weight: 1
                      maxPlayers: 200
            """;

    private ConfigDefaults() {
    }

    /**
     * Render the default configuration template.
     */
    public static String defaultYaml(String referralKeyPath) {
        if (referralKeyPath == null || referralKeyPath.isBlank()) {
            throw new ConfigException("Referral key path is required for the default config");
        }
        return DEFAULT_YAML_TEMPLATE.formatted(referralKeyPath, referralKeyPath);
    }
}
