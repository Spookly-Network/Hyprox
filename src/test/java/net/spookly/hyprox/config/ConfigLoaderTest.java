package net.spookly.hyprox.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ConfigLoaderTest {
    @Test
    void createsDefaultConfigWhenMissing() throws IOException {
        Path tempDir = Files.createTempDirectory("hyprox-config");
        Path configPath = tempDir.resolve("hyprox.yaml");

        ConfigException exception = assertThrows(ConfigException.class, () -> ConfigLoader.load(configPath));

        assertTrue(Files.exists(configPath));
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("proxy:"));
        assertTrue(content.contains("path:secret/referral_hmac"));
        Path secretPath = tempDir.resolve("secret").resolve("referral_hmac");
        assertTrue(Files.exists(secretPath));
        assertFalse(Files.readString(secretPath, StandardCharsets.UTF_8).isBlank());
        assertTrue(exception.getMessage().contains("generated default"));
    }

    @Test
    void expandsPathSecretsRelativeToConfig() throws IOException {
        Path tempDir = Files.createTempDirectory("hyprox-config");
        Path configPath = tempDir.resolve("hyprox.yaml");
        Path secretPath = tempDir.resolve("secret").resolve("referral_hmac");
        Files.createDirectories(secretPath.getParent());
        Files.writeString(secretPath, "test-secret", StandardCharsets.UTF_8);
        Files.writeString(
                configPath,
                ConfigDefaults.defaultYaml("secret/referral_hmac"),
                StandardCharsets.UTF_8
        );

        HyproxConfig config = ConfigLoader.load(configPath);

        assertEquals("test-secret", config.auth.referral.signing.keys.get(0).key);
        assertEquals(tempDir.resolve("certs").resolve("proxy.crt").toString(), config.proxy.quic.cert);
        assertEquals(tempDir.resolve("certs").resolve("proxy.key").toString(), config.proxy.quic.key);
    }
}
