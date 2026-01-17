package net.spookly.hyprox.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EnvExpander {
    private EnvExpander() {
    }

    static Object expand(Object value, Path baseDir) {
        if (value instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) value;
            Map<Object, Object> expanded = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                expanded.put(entry.getKey(), expand(entry.getValue(), baseDir));
            }
            return expanded;
        }
        if (value instanceof List) {
            List<?> raw = (List<?>) value;
            List<Object> expanded = new ArrayList<>(raw.size());
            for (Object item : raw) {
                expanded.add(expand(item, baseDir));
            }
            return expanded;
        }
        if (value instanceof String) {
            String raw = (String) value;
            if (raw.startsWith("env:")) {
                String key = raw.substring("env:".length());
                String envValue = System.getenv(key);
                if (envValue == null) {
                    throw new ConfigException("Missing required environment variable: " + key);
                }
                return envValue;
            }
            if (raw.startsWith("path:")) {
                String location = raw.substring("path:".length());
                if (location.isBlank()) {
                    throw new ConfigException("Path value is empty");
                }
                Path resolved = resolvePath(baseDir, location);
                try {
                    String content = Files.readString(resolved, StandardCharsets.UTF_8).stripTrailing();
                    if (content.isEmpty()) {
                        throw new ConfigException("Path value is empty: " + resolved);
                    }
                    return content;
                } catch (IOException e) {
                    throw new ConfigException("Failed to read config path: " + resolved, e);
                }
            }
        }
        return value;
    }

    private static Path resolvePath(Path baseDir, String rawValue) {
        try {
            Path path = Paths.get(rawValue);
            if (baseDir != null && !path.isAbsolute()) {
                return baseDir.resolve(path).normalize();
            }
            return path;
        } catch (Exception e) {
            throw new ConfigException("Invalid path value: " + rawValue, e);
        }
    }
}
