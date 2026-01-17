package net.spookly.hyprox.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigWarningsTest {
    @Test
    void warnsOnWorldReadableKey(@TempDir Path tempDir) throws Exception {
        Path keyFile = tempDir.resolve("proxy.key");
        Files.writeString(keyFile, "dummy");
        PosixFileAttributeView view = Files.getFileAttributeView(keyFile, PosixFileAttributeView.class);
        Assumptions.assumeTrue(view != null, "POSIX permissions not supported");
        Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OTHERS_READ
        );
        Files.setPosixFilePermissions(keyFile, perms);

        HyproxConfig config = new HyproxConfig();
        config.proxy = new HyproxConfig.ProxyConfig();
        config.proxy.quic = new HyproxConfig.QuicConfig();
        config.proxy.quic.key = keyFile.getFileName().toString();

        List<String> warnings = ConfigWarnings.collect(config, tempDir.resolve("hyprox.yaml"));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("proxy.quic.key")));
    }
}
