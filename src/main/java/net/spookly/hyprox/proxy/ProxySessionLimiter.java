package net.spookly.hyprox.proxy;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces per-IP handshake rate limits and concurrent session caps.
 */
public final class ProxySessionLimiter {
    private static final long WINDOW_MILLIS = 60_000L;

    private final Clock clock;
    private final Integer maxHandshakesPerMinute;
    private final Integer maxConcurrentSessions;
    private final Map<String, Deque<Long>> handshakeTimestampsByIp = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> concurrentSessionsByIp = new ConcurrentHashMap<>();

    public ProxySessionLimiter(Integer maxHandshakesPerMinute, Integer maxConcurrentSessions, Clock clock) {
        this.maxHandshakesPerMinute = normalizeLimit(maxHandshakesPerMinute);
        this.maxConcurrentSessions = normalizeLimit(maxConcurrentSessions);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public boolean tryAcquireHandshake(String ipAddress) {
        if (maxHandshakesPerMinute == null || ipAddress == null) {
            return true;
        }
        Deque<Long> timestamps = handshakeTimestampsByIp.computeIfAbsent(ipAddress, ignored -> new ArrayDeque<>());
        long now = clock.millis();
        long cutoff = now - WINDOW_MILLIS;
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxHandshakesPerMinute) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    public boolean tryOpenSession(String ipAddress) {
        if (maxConcurrentSessions == null || ipAddress == null) {
            return true;
        }
        AtomicInteger count = concurrentSessionsByIp.computeIfAbsent(ipAddress, ignored -> new AtomicInteger());
        int current = count.incrementAndGet();
        if (current > maxConcurrentSessions) {
            count.decrementAndGet();
            cleanupIfZero(ipAddress, count);
            return false;
        }
        return true;
    }

    public void releaseSession(String ipAddress) {
        if (maxConcurrentSessions == null || ipAddress == null) {
            return;
        }
        AtomicInteger count = concurrentSessionsByIp.get(ipAddress);
        if (count == null) {
            return;
        }
        int current = count.decrementAndGet();
        if (current <= 0) {
            cleanupIfZero(ipAddress, count);
        }
    }

    private void cleanupIfZero(String ipAddress, AtomicInteger count) {
        if (count.get() <= 0) {
            concurrentSessionsByIp.remove(ipAddress, count);
        }
    }

    private Integer normalizeLimit(Integer rawValue) {
        if (rawValue == null || rawValue <= 0) {
            return null;
        }
        return rawValue;
    }
}
