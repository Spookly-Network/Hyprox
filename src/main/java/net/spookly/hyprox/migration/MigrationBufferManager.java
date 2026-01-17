package net.spookly.hyprox.migration;

import net.spookly.hyprox.config.ConfigException;
import net.spookly.hyprox.config.HyproxConfig;

/**
 * Tracks global buffer caps for concurrent migration attempts.
 */
public final class MigrationBufferManager {
    /**
     * Maximum buffered packets per migration.
     */
    private final int maxBufferPackets;
    /**
     * Maximum buffered packets across all migrations.
     */
    private final int maxGlobalPackets;
    /**
     * Current global buffered packet count.
     */
    private int globalBuffered;

    public MigrationBufferManager(HyproxConfig config) {
        if (config == null) {
            throw new ConfigException("Config is required for migration buffering");
        }
        HyproxConfig.MigrationConfig migration = config.migration;
        if (migration == null) {
            throw new ConfigException("migration config is required for migration buffering");
        }
        this.maxBufferPackets = requirePositive(migration.bufferMaxPackets, "migration.bufferMaxPackets");
        this.maxGlobalPackets = requirePositive(migration.bufferGlobalMaxPackets, "migration.bufferGlobalMaxPackets");
    }

    public int maxBufferPackets() {
        return maxBufferPackets;
    }

    public int maxGlobalPackets() {
        return maxGlobalPackets;
    }

    public synchronized int globalBufferedCount() {
        return globalBuffered;
    }

    public <T> MigrationBuffer<T> createBuffer(MigrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("migration context is required");
        }
        return new MigrationBuffer<>(this, context, maxBufferPackets);
    }

    synchronized boolean tryReserve(int count) {
        if (count <= 0) {
            return true;
        }
        if (globalBuffered + count > maxGlobalPackets) {
            return false;
        }
        globalBuffered += count;
        return true;
    }

    synchronized void release(int count) {
        if (count <= 0) {
            return;
        }
        if (count > globalBuffered) {
            globalBuffered = 0;
            throw new IllegalStateException("migration global buffer underflow");
        }
        globalBuffered -= count;
    }

    private int requirePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new ConfigException(field + " must be greater than 0");
        }
        return value;
    }
}
