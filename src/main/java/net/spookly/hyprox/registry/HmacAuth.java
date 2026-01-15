package net.spookly.hyprox.registry;

import lombok.AllArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * HMAC-based request authentication for the registry control plane.
 */
public final class HmacAuth {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private HmacAuth() {
    }

    /**
     * Verify request headers, timestamp skew, replay, and signature.
     */
    public static AuthResult verify(RequestAuthData data,
                                    String sharedKey,
                                    int nonceBytes,
                                    int clockSkewSeconds,
                                    NonceCache nonceCache) {
        if (sharedKey == null || sharedKey.trim().isEmpty()) {
            return AuthResult.error("shared key is missing");
        }
        if (data == null) {
            return AuthResult.error("auth data missing");
        }
        if (data.timestampSeconds == null) {
            return AuthResult.error("missing X-Hyprox-Timestamp header");
        }
        if (data.nonce == null || data.nonce.trim().isEmpty()) {
            return AuthResult.error("missing X-Hyprox-Nonce header");
        }
        if (data.signature == null || data.signature.trim().isEmpty()) {
            return AuthResult.error("missing X-Hyprox-Signature header");
        }
        if (data.orchestratorId == null || data.orchestratorId.trim().isEmpty()) {
            return AuthResult.error("missing X-Hyprox-Orchestrator header");
        }
        if (nonceBytes > 0 && data.nonce.length() < nonceBytes) {
            return AuthResult.error("nonce is shorter than expected");
        }

        Instant now = Instant.now();
        Instant requestTime = Instant.ofEpochSecond(data.timestampSeconds);
        long skew = Math.abs(now.getEpochSecond() - requestTime.getEpochSecond());
        if (skew > clockSkewSeconds) {
            return AuthResult.error("timestamp outside allowed skew");
        }

        String canonical = canonicalString(data.method, data.path, data.timestampSeconds, data.nonce, data.body);
        String expected = sign(canonical, sharedKey);
        if (!constantTimeEquals(expected, data.signature)) {
            return AuthResult.error("invalid signature");
        }

        String nonceKey = data.orchestratorId + ":" + data.nonce;
        if (!nonceCache.register(nonceKey, requestTime)) {
            return AuthResult.error("replayed nonce");
        }

        return AuthResult.ok();
    }

    private static String canonicalString(String method, String path, long timestamp, String nonce, byte[] body) {
        String payload = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        return method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + payload;
    }

    private static String sign(String canonical, String sharedKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sharedKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    @AllArgsConstructor
    public static final class RequestAuthData {
        public final String method;
        public final String path;
        public final Long timestampSeconds;
        public final String nonce;
        public final String signature;
        public final String orchestratorId;
        public final byte[] body;
    }

    @AllArgsConstructor
    public static final class AuthResult {
        public final boolean ok;
        public final String message;

        public static AuthResult ok() {
            return new AuthResult(true, null);
        }

        public static AuthResult error(String message) {
            return new AuthResult(false, message);
        }
    }
}
