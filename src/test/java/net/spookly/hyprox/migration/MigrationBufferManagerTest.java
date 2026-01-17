package net.spookly.hyprox.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.spookly.hyprox.config.HyproxConfig;
import org.junit.jupiter.api.Test;

class MigrationBufferManagerTest {
    @Test
    void enforcesPerBufferLimit() {
        MigrationBufferManager manager = new MigrationBufferManager(config(2, 10));
        MigrationBuffer<String> buffer = manager.createBuffer(context());

        assertTrue(buffer.add("one").ok());
        assertTrue(buffer.add("two").ok());

        MigrationBuffer.BufferResult third = buffer.add("three");
        assertFalse(third.ok());
        assertTrue(third.rollback());
        assertEquals("migration buffer limit exceeded", third.error());
        assertEquals(2, manager.globalBufferedCount());
    }

    @Test
    void enforcesGlobalLimitAcrossBuffers() {
        MigrationBufferManager manager = new MigrationBufferManager(config(10, 3));
        MigrationBuffer<String> first = manager.createBuffer(context());
        MigrationBuffer<String> second = manager.createBuffer(context());

        assertTrue(first.add("one").ok());
        assertTrue(first.add("two").ok());
        assertTrue(second.add("three").ok());

        MigrationBuffer.BufferResult fourth = second.add("four");
        assertFalse(fourth.ok());
        assertTrue(fourth.rollback());
        assertEquals("migration global buffer limit exceeded", fourth.error());
        assertEquals(3, manager.globalBufferedCount());

        List<String> drained = first.drain();
        assertEquals(2, drained.size());
        assertEquals(1, manager.globalBufferedCount());

        assertTrue(second.add("four").ok());
        assertEquals(2, manager.globalBufferedCount());
    }

    @Test
    void closeReleasesGlobalCount() {
        MigrationBufferManager manager = new MigrationBufferManager(config(5, 5));
        MigrationBuffer<String> buffer = manager.createBuffer(context());

        assertTrue(buffer.add("one").ok());
        assertTrue(buffer.add("two").ok());
        assertEquals(2, manager.globalBufferedCount());

        buffer.close();
        assertEquals(0, manager.globalBufferedCount());

        MigrationBuffer.BufferResult result = buffer.add("three");
        assertFalse(result.ok());
        assertFalse(result.rollback());
        assertEquals("migration buffer is closed", result.error());
    }

    private HyproxConfig config(int bufferMaxPackets, int bufferGlobalMaxPackets) {
        HyproxConfig config = new HyproxConfig();
        config.migration = new HyproxConfig.MigrationConfig();
        config.migration.enabled = true;
        config.migration.bufferMaxPackets = bufferMaxPackets;
        config.migration.bufferGlobalMaxPackets = bufferGlobalMaxPackets;
        return config;
    }

    private MigrationContext context() {
        return new MigrationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "backend-a",
                "backend-b"
        );
    }
}
