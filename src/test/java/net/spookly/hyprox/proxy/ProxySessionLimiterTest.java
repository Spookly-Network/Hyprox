package net.spookly.hyprox.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class ProxySessionLimiterTest {
    @Test
    void enforcesHandshakeRateLimit() {
        MutableClock clock = new MutableClock();
        ProxySessionLimiter limiter = new ProxySessionLimiter(2, null, clock);
        assertTrue(limiter.tryAcquireHandshake("10.0.0.1"));
        assertTrue(limiter.tryAcquireHandshake("10.0.0.1"));
        assertFalse(limiter.tryAcquireHandshake("10.0.0.1"));

        clock.advanceSeconds(61);
        assertTrue(limiter.tryAcquireHandshake("10.0.0.1"));
    }

    @Test
    void enforcesConcurrentSessionLimit() {
        ProxySessionLimiter limiter = new ProxySessionLimiter(null, 1, Clock.systemUTC());
        assertTrue(limiter.tryOpenSession("10.0.0.2"));
        assertFalse(limiter.tryOpenSession("10.0.0.2"));
        limiter.releaseSession("10.0.0.2");
        assertTrue(limiter.tryOpenSession("10.0.0.2"));
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong();

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public long millis() {
            return millis.get();
        }

        void advanceSeconds(long seconds) {
            millis.addAndGet(seconds * 1000L);
        }
    }
}
