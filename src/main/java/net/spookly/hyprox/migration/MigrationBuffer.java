package net.spookly.hyprox.migration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Bounded per-migration buffer with rollback signaling.
 *
 * @param <T> buffered item type
 */
public final class MigrationBuffer<T> {
    private static final String ERROR_ITEM_REQUIRED = "migration buffer item is required";
    private static final String ERROR_BUFFER_CLOSED = "migration buffer is closed";
    private static final String ERROR_BUFFER_LIMIT = "migration buffer limit exceeded";
    private static final String ERROR_GLOBAL_LIMIT = "migration global buffer limit exceeded";

    /**
     * Global buffer manager coordinating shared caps.
     */
    private final MigrationBufferManager manager;
    /**
     * Migration metadata for this buffer.
     */
    private final MigrationContext context;
    /**
     * Maximum buffered packets for this migration.
     */
    private final int maxPackets;
    /**
     * FIFO packet buffer.
     */
    private final Deque<T> queue = new ArrayDeque<>();
    /**
     * Whether the buffer is closed to new packets.
     */
    private boolean closed;

    MigrationBuffer(MigrationBufferManager manager, MigrationContext context, int maxPackets) {
        this.manager = manager;
        this.context = context;
        this.maxPackets = maxPackets;
    }

    public MigrationContext context() {
        return context;
    }

    public int maxPackets() {
        return maxPackets;
    }

    public synchronized int bufferedCount() {
        return queue.size();
    }

    public synchronized boolean closed() {
        return closed;
    }

    /**
     * Add an item to the buffer, signaling rollback when capacity is exceeded.
     */
    public BufferResult add(T item) {
        if (item == null) {
            return BufferResult.error(ERROR_ITEM_REQUIRED);
        }
        synchronized (this) {
            if (closed) {
                return BufferResult.error(ERROR_BUFFER_CLOSED);
            }
            if (queue.size() >= maxPackets) {
                return BufferResult.rollback(ERROR_BUFFER_LIMIT);
            }
            if (!manager.tryReserve(1)) {
                return BufferResult.rollback(ERROR_GLOBAL_LIMIT);
            }
            queue.add(item);
            return BufferResult.success();
        }
    }

    /**
     * Drain buffered items in FIFO order.
     */
    public List<T> drain() {
        synchronized (this) {
            if (queue.isEmpty()) {
                return Collections.emptyList();
            }
            List<T> drained = new ArrayList<>(queue);
            releaseAllLocked();
            return drained;
        }
    }

    /**
     * Close the buffer and discard any queued items.
     */
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            releaseAllLocked();
        }
    }

    private void releaseAllLocked() {
        int count = queue.size();
        queue.clear();
        manager.release(count);
    }

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public static final class BufferResult {
        /**
         * Whether the buffer mutation succeeded.
         */
        private final boolean ok;
        /**
         * Whether the caller should roll back the migration.
         */
        private final boolean rollback;
        /**
         * Failure reason when ok is false.
         */
        private final String error;

        public static BufferResult success() {
            return new BufferResult(true, false, null);
        }

        public static BufferResult error(String message) {
            return new BufferResult(false, false, message);
        }

        public static BufferResult rollback(String message) {
            return new BufferResult(false, true, message);
        }
    }
}
