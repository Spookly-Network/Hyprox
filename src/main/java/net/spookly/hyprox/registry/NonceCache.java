package net.spookly.hyprox.registry;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NonceCache {
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();
    private final int ttlSeconds;

    public NonceCache(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public boolean register(String key, Instant timestamp) {
        purge(timestamp);
        return seen.putIfAbsent(key, timestamp) == null;
    }

    private void purge(Instant now) {
        Instant cutoff = now.minusSeconds(ttlSeconds);
        for (Map.Entry<String, Instant> entry : seen.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                seen.remove(entry.getKey());
            }
        }
    }
}
