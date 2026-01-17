package net.spookly.hyprox.config;

/**
 * Default configuration template written when no config file exists.
 */
public final class ConfigDefaults {
    private static final String DEFAULT_YAML = """
            # Generated default Hyprox config.
            # Update cert paths and set HYPROX_REFERRAL_HMAC before running.
            proxy:
              listen:
                host: 0.0.0.0
                port: 20000
              mode: redirect
              defaultPath: redirect
              quic:
                alpn: ["hytale/1"]
                cert: config/certs/proxy.crt
                key: config/certs/proxy.key
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
                      key: env:HYPROX_REFERRAL_HMAC
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
    public static String defaultYaml() {
        return DEFAULT_YAML;
    }
}
