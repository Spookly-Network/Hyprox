package net.spookly.hyprox.config;

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
        assertTrue(exception.getMessage().contains("generated default"));
    }
}
