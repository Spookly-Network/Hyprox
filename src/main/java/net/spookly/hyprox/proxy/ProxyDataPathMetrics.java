package net.spookly.hyprox.proxy;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks per-session counters for the full proxy data path.
 */
public final class ProxyDataPathMetrics {
    private final AtomicLong clientToBackendPackets = new AtomicLong();
    private final AtomicLong backendToClientPackets = new AtomicLong();
    private final AtomicLong clientToBackendBytes = new AtomicLong();
    private final AtomicLong backendToClientBytes = new AtomicLong();
    private final AtomicLong backendConnectLatencyNanos = new AtomicLong();

    public void recordClientToBackendPacket() {
        clientToBackendPackets.incrementAndGet();
    }

    public void recordBackendToClientPacket() {
        backendToClientPackets.incrementAndGet();
    }

    public void recordClientToBackendBytes(long bytes) {
        if (bytes > 0) {
            clientToBackendBytes.addAndGet(bytes);
        }
    }

    public void recordBackendToClientBytes(long bytes) {
        if (bytes > 0) {
            backendToClientBytes.addAndGet(bytes);
        }
    }

    public void recordBackendConnectLatencyNanos(long latencyNanos) {
        if (latencyNanos > 0) {
            backendConnectLatencyNanos.set(latencyNanos);
        }
    }

    public long clientToBackendPackets() {
        return clientToBackendPackets.get();
    }

    public long backendToClientPackets() {
        return backendToClientPackets.get();
    }

    public long clientToBackendBytes() {
        return clientToBackendBytes.get();
    }

    public long backendToClientBytes() {
        return backendToClientBytes.get();
    }

    public long backendConnectLatencyNanos() {
        return backendConnectLatencyNanos.get();
    }
}
