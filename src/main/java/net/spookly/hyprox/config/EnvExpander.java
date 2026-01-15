package net.spookly.hyprox.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EnvExpander {
    private EnvExpander() {
    }

    static Object expand(Object value) {
        if (value instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) value;
            Map<Object, Object> expanded = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                expanded.put(entry.getKey(), expand(entry.getValue()));
            }
            return expanded;
        }
        if (value instanceof List) {
            List<?> raw = (List<?>) value;
            List<Object> expanded = new ArrayList<>(raw.size());
            for (Object item : raw) {
                expanded.add(expand(item));
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
        }
        return value;
    }
}
