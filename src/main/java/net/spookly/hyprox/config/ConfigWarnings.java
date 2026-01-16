package net.spookly.hyprox.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Collects non-fatal configuration warnings (for example, insecure file permissions).
 */
public final class ConfigWarnings {
    private ConfigWarnings() {
    }

    public static List<String> collect(HyproxConfig config, Path configPath) {
        List<String> warnings = new ArrayList<>();
        if (config == null) {
            return warnings;
        }
        Path baseDir = configPath == null ? null : configPath.getParent();
        HyproxConfig.ProxyConfig proxy = config.proxy;
        if (proxy != null && proxy.quic != null) {
            HyproxConfig.QuicConfig quic = proxy.quic;
            warnIfWorldReadable(warnings, "proxy.quic.cert", quic.cert, baseDir, true);
            warnIfWorldReadable(warnings, "proxy.quic.key", quic.key, baseDir, true);
            warnIfWorldReadable(warnings, "proxy.quic.clientCa", quic.clientCa, baseDir, true);
            warnIfWorldReadable(warnings, "proxy.quic.backendCa", quic.backendCa, baseDir, true);
        }
        HyproxConfig.AgentConfig agent = config.agent;
        if (agent != null && agent.auth != null) {
            warnIfWorldReadable(warnings, "agent.auth.clientCa", agent.auth.clientCa, baseDir, true);
            warnIfWorldReadable(warnings, "agent.auth.sharedKey", agent.auth.sharedKey, baseDir, false);
        }
        HyproxConfig.RegistryConfig registry = config.registry;
        if (registry != null && registry.auth != null) {
            warnIfWorldReadable(warnings, "registry.auth.clientCa", registry.auth.clientCa, baseDir, true);
            warnIfWorldReadable(warnings, "registry.auth.sharedKey", registry.auth.sharedKey, baseDir, false);
        }
        return warnings;
    }

    private static void warnIfWorldReadable(List<String> warnings, String label, String value, Path baseDir, boolean requireFile) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        Path resolved = resolvePath(baseDir, value.trim());
        if (resolved == null || !Files.exists(resolved)) {
            return;
        }
        if (requireFile && !Files.isRegularFile(resolved)) {
            return;
        }
        PosixFileAttributeView view = Files.getFileAttributeView(resolved, PosixFileAttributeView.class);
        if (view == null) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = view.readAttributes().permissions();
            if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
                warnings.add(label + " is world-readable: " + resolved);
            }
        } catch (IOException ignored) {
            // Ignore file permission read failures.
        }
    }

    private static Path resolvePath(Path baseDir, String rawValue) {
        try {
            Path path = Paths.get(rawValue);
            if (baseDir != null && !path.isAbsolute()) {
                return baseDir.resolve(path).normalize();
            }
            return path;
        } catch (Exception ignored) {
            return null;
        }
    }
}
