package net.spookly.hyprox.proxy;

import io.netty.util.AttributeKey;
import net.spookly.hyprox.auth.TokenRedactor;

/**
 * Stores sensitive authentication tokens for a proxied session in terminate mode.
 */
public final class ProxyAuthSession {
    /**
     * Channel attribute key for storing auth session data.
     */
    public static final AttributeKey<ProxyAuthSession> AUTH_SESSION =
            AttributeKey.valueOf("hyprox.auth.session");
    /**
     * Identity token presented during the initial Connect handshake.
     */
    private String identityToken;
    /**
     * Access token sent by the client during authenticated login.
     */
    private String accessToken;
    /**
     * Authorization grant sent by the client for server mutual auth.
     */
    private String serverAuthorizationGrant;
    /**
     * Access token sent by the backend to the client for mutual auth.
     */
    private String serverAccessToken;

    /**
     * Capture the identity token from the initial handshake.
     *
     * @param identityToken raw identity token value
     */
    public void captureIdentityToken(String identityToken) {
        if (isBlank(identityToken)) {
            return;
        }
        this.identityToken = identityToken;
    }

    /**
     * Capture tokens from the client AuthToken packet.
     *
     * @param accessToken raw access token
     * @param serverAuthorizationGrant raw server authorization grant
     */
    public void captureAuthToken(String accessToken, String serverAuthorizationGrant) {
        if (!isBlank(accessToken)) {
            this.accessToken = accessToken;
        }
        if (!isBlank(serverAuthorizationGrant)) {
            this.serverAuthorizationGrant = serverAuthorizationGrant;
        }
    }

    /**
     * Capture the backend server access token from ServerAuthToken.
     *
     * @param serverAccessToken raw server access token
     */
    public void captureServerAccessToken(String serverAccessToken) {
        if (isBlank(serverAccessToken)) {
            return;
        }
        this.serverAccessToken = serverAccessToken;
    }

    /**
     * Clear all stored tokens.
     */
    public void clearSensitive() {
        identityToken = null;
        accessToken = null;
        serverAuthorizationGrant = null;
        serverAccessToken = null;
    }

    /**
     * Return the last captured identity token.
     */
    public String identityToken() {
        return identityToken;
    }

    /**
     * Return the last captured access token.
     */
    public String accessToken() {
        return accessToken;
    }

    /**
     * Return the last captured server authorization grant.
     */
    public String serverAuthorizationGrant() {
        return serverAuthorizationGrant;
    }

    /**
     * Return the last captured server access token.
     */
    public String serverAccessToken() {
        return serverAccessToken;
    }

    /**
     * Redacted summary safe for logs and trace output.
     */
    public String redactedSummary() {
        StringBuilder builder = new StringBuilder("ProxyAuthSession{");
        builder.append("identityToken=").append(TokenRedactor.redact(identityToken));
        builder.append(", accessToken=").append(TokenRedactor.redact(accessToken));
        builder.append(", serverAuthorizationGrant=").append(TokenRedactor.redact(serverAuthorizationGrant));
        builder.append(", serverAccessToken=").append(TokenRedactor.redact(serverAccessToken));
        builder.append('}');
        return builder.toString();
    }

    @Override
    public String toString() {
        return redactedSummary();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
