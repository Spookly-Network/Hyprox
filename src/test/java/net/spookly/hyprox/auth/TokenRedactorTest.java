package net.spookly.hyprox.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenRedactorTest {
    @Test
    void redactNullReturnsNull() {
        assertNull(TokenRedactor.redact(null));
    }

    @Test
    void redactEmptyReturnsEmpty() {
        assertEquals("", TokenRedactor.redact(""));
    }

    @Test
    void redactValueReturnsRedacted() {
        assertEquals("REDACTED", TokenRedactor.redact("token"));
    }
}
