package net.spookly.hyprox.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;

import org.junit.jupiter.api.Test;

class CidrMatcherTest {
    @Test
    void matchesAllowedNetworks() throws Exception {
        CidrMatcher matcher = CidrMatcher.from(List.of("10.0.0.0/8", "192.168.1.0/24"));
        assertTrue(matcher.isAllowed(InetAddress.getByName("10.1.2.3")));
        assertTrue(matcher.isAllowed(InetAddress.getByName("192.168.1.42")));
        assertFalse(matcher.isAllowed(InetAddress.getByName("172.16.0.1")));
    }
}
