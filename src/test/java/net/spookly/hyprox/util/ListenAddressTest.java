package net.spookly.hyprox.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ListenAddressTest {
    @Test
    void parsesHostAndPort() {
        ListenAddress address = ListenAddress.parse("127.0.0.1:9000");
        assertEquals("127.0.0.1", address.host());
        assertEquals(9000, address.port());
    }

    @Test
    void rejectsInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> ListenAddress.parse("bad"));
    }
}
