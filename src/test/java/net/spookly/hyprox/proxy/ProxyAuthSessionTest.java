package net.spookly.hyprox.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProxyAuthSessionTest {
    @Test
    void capturesTokens() {
        ProxyAuthSession session = new ProxyAuthSession();
        session.captureIdentityToken("identity-token");
        session.captureAuthToken("access-token", "auth-grant");
        session.captureServerAccessToken("server-access-token");

        assertEquals("identity-token", session.identityToken());
        assertEquals("access-token", session.accessToken());
        assertEquals("auth-grant", session.serverAuthorizationGrant());
        assertEquals("server-access-token", session.serverAccessToken());
    }

    @Test
    void clearsSensitiveTokens() {
        ProxyAuthSession session = new ProxyAuthSession();
        session.captureIdentityToken("identity-token");
        session.captureAuthToken("access-token", "auth-grant");
        session.captureServerAccessToken("server-access-token");

        session.clearSensitive();

        assertNull(session.identityToken());
        assertNull(session.accessToken());
        assertNull(session.serverAuthorizationGrant());
        assertNull(session.serverAccessToken());
    }

    @Test
    void redactedSummaryDoesNotExposeTokens() {
        ProxyAuthSession session = new ProxyAuthSession();
        session.captureIdentityToken("identity-token");
        session.captureAuthToken("access-token", "auth-grant");
        session.captureServerAccessToken("server-access-token");

        String summary = session.redactedSummary();

        assertTrue(summary.contains("REDACTED"));
        assertFalse(summary.contains("identity-token"));
        assertFalse(summary.contains("access-token"));
        assertFalse(summary.contains("auth-grant"));
        assertFalse(summary.contains("server-access-token"));
    }
}
