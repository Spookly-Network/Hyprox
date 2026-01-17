package net.spookly.hyprox.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

public final class ConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private ConfigLoader() {
    }

    /**
     * Load and validate the Hyprox YAML configuration.
     */
    public static HyproxConfig load(Path path) {
        if (path == null) {
            throw new ConfigException("Config path is required");
        }
        if (!Files.exists(path)) {
            throw new ConfigException("Config file does not exist: " + path);
        }
        Object raw;
        Yaml yaml = new Yaml();
        try (Reader reader = Files.newBufferedReader(path)) {
            raw = yaml.load(reader);
        } catch (IOException e) {
            throw new ConfigException("Failed to read config: " + path, e);
        }
        if (raw == null) {
            throw new ConfigException("Config file is empty: " + path);
        }
        Object expanded = EnvExpander.expand(raw);
        HyproxConfig config;
        try {
            config = MAPPER.convertValue(expanded, HyproxConfig.class);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Failed to parse config: " + path, e);
        }
        ConfigValidator.validate(config);
        return config;
    }
}
