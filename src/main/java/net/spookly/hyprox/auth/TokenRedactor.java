package net.spookly.hyprox.auth;

/**
 * Redacts sensitive token values for logs and trace output.
 */
public final class TokenRedactor {
    private static final String REDACTED = "REDACTED";

    private TokenRedactor() {
    }

    /**
     * Return a safe representation of a sensitive token for logging.
     *
     * @param token the raw token value
     * @return {@code null} when the token is {@code null}, otherwise {@code REDACTED}
     */
    public static String redact(String token) {
        if (token == null) {
            return null;
        }
        if (token.isEmpty()) {
            return "";
        }
        return REDACTED;
    }
}
