package net.spookly.hyprox.config;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves relative file path settings against the config directory.
 */
final class ConfigPathResolver {
    private ConfigPathResolver() {
    }

    static void resolve(HyproxConfig config, Path baseDir) {
        if (config == null || baseDir == null) {
            return;
        }
        HyproxConfig.ProxyConfig proxy = config.proxy;
        if (proxy != null && proxy.quic != null) {
            HyproxConfig.QuicConfig quic = proxy.quic;
            quic.cert = resolvePath(baseDir, quic.cert);
            quic.key = resolvePath(baseDir, quic.key);
            quic.clientCa = resolvePath(baseDir, quic.clientCa);
            quic.backendCa = resolvePath(baseDir, quic.backendCa);
        }
        HyproxConfig.AgentConfig agent = config.agent;
        if (agent != null && agent.auth != null) {
            agent.auth.clientCa = resolvePath(baseDir, agent.auth.clientCa);
        }
        HyproxConfig.RegistryConfig registry = config.registry;
        if (registry != null && registry.auth != null) {
            registry.auth.clientCa = resolvePath(baseDir, registry.auth.clientCa);
        }
    }

    private static String resolvePath(Path baseDir, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return rawValue;
        }
        try {
            Path path = Paths.get(rawValue);
            if (!path.isAbsolute()) {
                path = baseDir.resolve(path).normalize();
            }
            return path.toString();
        } catch (InvalidPathException e) {
            throw new ConfigException("Invalid path value: " + rawValue, e);
        }
    }
}
