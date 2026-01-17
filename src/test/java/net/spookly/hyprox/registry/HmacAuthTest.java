package net.spookly.hyprox.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class HmacAuthTest {
    @Test
    void verifiesValidSignature() {
        String sharedKey = "super-secret";
        String nonce = "nonce-1234567890";
        long timestamp = Instant.now().getEpochSecond();
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String path = "/v1/registry/register";
        String signature = sign("POST", path, timestamp, nonce, body, sharedKey);

        HmacAuth.RequestAuthData data = new HmacAuth.RequestAuthData(
                "POST",
                path,
                timestamp,
                nonce,
                signature,
                "orch-1",
                body
        );
        NonceCache cache = new NonceCache(60);
        HmacAuth.AuthResult result = HmacAuth.verify(data, sharedKey, 8, 10, cache);
        assertTrue(result.ok, result.message);
    }

    @Test
    void rejectsReplayedNonce() {
        String sharedKey = "super-secret";
        String nonce = "nonce-1234567890";
        long timestamp = Instant.now().getEpochSecond();
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String path = "/v1/registry/register";
        String signature = sign("POST", path, timestamp, nonce, body, sharedKey);
        HmacAuth.RequestAuthData data = new HmacAuth.RequestAuthData(
                "POST",
                path,
                timestamp,
                nonce,
                signature,
                "orch-1",
                body
        );
        NonceCache cache = new NonceCache(60);
        assertTrue(HmacAuth.verify(data, sharedKey, 8, 10, cache).ok);
        assertFalse(HmacAuth.verify(data, sharedKey, 8, 10, cache).ok);
    }

    @Test
    void rejectsSkewedTimestamp() {
        String sharedKey = "super-secret";
        String nonce = "nonce-1234567890";
        long timestamp = Instant.now().minusSeconds(600).getEpochSecond();
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String path = "/v1/registry/register";
        String signature = sign("POST", path, timestamp, nonce, body, sharedKey);
        HmacAuth.RequestAuthData data = new HmacAuth.RequestAuthData(
                "POST",
                path,
                timestamp,
                nonce,
                signature,
                "orch-1",
                body
        );
        NonceCache cache = new NonceCache(60);
        HmacAuth.AuthResult result = HmacAuth.verify(data, sharedKey, 8, 10, cache);
        assertFalse(result.ok);
    }

    @Test
    void rejectsShortNonce() {
        String sharedKey = "super-secret";
        String nonce = "abc";
        long timestamp = Instant.now().getEpochSecond();
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String path = "/v1/registry/register";
        String signature = sign("POST", path, timestamp, nonce, body, sharedKey);
        HmacAuth.RequestAuthData data = new HmacAuth.RequestAuthData(
                "POST",
                path,
                timestamp,
                nonce,
                signature,
                "orch-1",
                body
        );
        NonceCache cache = new NonceCache(60);
        HmacAuth.AuthResult result = HmacAuth.verify(data, sharedKey, 8, 10, cache);
        assertFalse(result.ok);
    }

    private String sign(String method, String path, long timestamp, String nonce, byte[] body, String sharedKey) {
        String payload = new String(body, StandardCharsets.UTF_8);
        String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
