package net.spookly.hyprox.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.spookly.hyprox.config.ConfigException;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.registry.NonceCache;
import net.spookly.hyprox.routing.BackendTarget;
import net.spookly.hyprox.routing.RoutingService;

/**
 * Signs and verifies referral payloads for secure redirect routing.
 */
public final class ReferralService {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RoutingService routingService;
    private final Clock clock;
    private final NonceCache nonceCache;
    private final NonceGenerator nonceGenerator;
    private final int payloadMaxBytes;
    private final int defaultTtlSeconds;
    private final int defaultNonceBytes;
    private final String activeKeyId;
    private final Map<String, ReferralSigningKey> keysById;

    public ReferralService(HyproxConfig config, RoutingService routingService) {
        this(config, routingService, Clock.systemUTC(), new SecureNonceGenerator());
    }

    ReferralService(HyproxConfig config, RoutingService routingService, Clock clock, NonceGenerator nonceGenerator) {
        if (config == null) {
            throw new ConfigException("Config is required for referral signing");
        }
        if (routingService == null) {
            throw new ConfigException("RoutingService is required for referral signing");
        }
        HyproxConfig.ReferralConfig referral = config.auth == null ? null : config.auth.referral;
        HyproxConfig.SigningConfig signing = referral == null ? null : referral.signing;
        if (signing == null) {
            throw new ConfigException("auth.referral.signing is required");
        }
        this.routingService = routingService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.nonceGenerator = nonceGenerator == null ? new SecureNonceGenerator() : nonceGenerator;
        this.payloadMaxBytes = referral != null && referral.payloadMaxBytes != null
                ? referral.payloadMaxBytes
                : 4096;
        this.defaultTtlSeconds = signing.ttlSeconds != null ? signing.ttlSeconds : 30;
        this.defaultNonceBytes = signing.nonceBytes != null ? signing.nonceBytes : 16;
        this.activeKeyId = signing.activeKeyId;
        this.keysById = buildKeys(signing);
        this.nonceCache = new NonceCache(defaultTtlSeconds);
    }

    /**
     * Build a signed referral payload for the target backend and client.
     */
    public SignResult signReferral(BackendTarget backend, UUID clientUuid) {
        if (backend == null) {
            return SignResult.error("backend is required");
        }
        if (clientUuid == null) {
            return SignResult.error("client uuid is required");
        }
        ReferralSigningKey key = keysById.get(activeKeyId);
        if (key == null) {
            return SignResult.error("active signing key is missing");
        }
        Instant now = clock.instant();
        if (!key.isValidFor(now)) {
            return SignResult.error("active signing key is outside its validity window");
        }
        if (!key.isAllowedForBackend(backend)) {
            return SignResult.error("active signing key scope does not allow backend");
        }
        String nonce = nonceGenerator.nextNonce(defaultNonceBytes);
        if (isBlank(nonce)) {
            return SignResult.error("nonce generation failed");
        }
        ReferralEnvelope envelope = new ReferralEnvelope(
                key.keyId,
                now.getEpochSecond(),
                defaultTtlSeconds,
                nonce,
                backend.id(),
                clientUuid.toString(),
                null
        );
        String signature = sign(canonical(envelope), key.secret);
        ReferralEnvelope signed = new ReferralEnvelope(
                envelope.keyId(),
                envelope.issuedAt(),
                envelope.ttlSeconds(),
                envelope.nonce(),
                envelope.targetBackendId(),
                envelope.clientUuid(),
                signature
        );
        byte[] payload = encode(signed);
        if (payload == null) {
            return SignResult.error("failed to encode referral payload");
        }
        if (payloadMaxBytes > 0 && payload.length > payloadMaxBytes) {
            return SignResult.error("referral payload exceeds configured max");
        }
        return SignResult.ok(payload);
    }

    /**
     * Verify referral payload data and return the validated backend target id.
     */
    public VerifyResult verifyReferral(byte[] payload, UUID clientUuid) {
        if (payload == null || payload.length == 0) {
            return VerifyResult.empty();
        }
        if (payloadMaxBytes > 0 && payload.length > payloadMaxBytes) {
            return VerifyResult.error("referral payload too large");
        }
        if (clientUuid == null) {
            return VerifyResult.error("client uuid is required");
        }
        ReferralEnvelope envelope;
        try {
            envelope = MAPPER.readValue(payload, ReferralEnvelope.class);
        } catch (IOException e) {
            return VerifyResult.error("referral payload is not valid JSON");
        }
        if (isBlank(envelope.keyId())
                || isBlank(envelope.nonce())
                || isBlank(envelope.targetBackendId())
                || isBlank(envelope.clientUuid())
                || isBlank(envelope.signature())
                || envelope.issuedAt() <= 0
                || envelope.ttlSeconds() <= 0) {
            return VerifyResult.error("referral payload is missing required fields");
        }
        if (!clientUuid.toString().equals(envelope.clientUuid())) {
            return VerifyResult.error("referral payload client uuid mismatch");
        }
        if (!hasExpectedNonceSize(envelope.nonce())) {
            return VerifyResult.error("referral nonce size is invalid");
        }
        if (envelope.ttlSeconds() > defaultTtlSeconds) {
            return VerifyResult.error("referral ttl exceeds configured max");
        }
        Instant issuedAt = Instant.ofEpochSecond(envelope.issuedAt());
        Instant now = clock.instant();
        if (issuedAt.isAfter(now.plusSeconds(envelope.ttlSeconds()))) {
            return VerifyResult.error("referral issuedAt is too far in the future");
        }
        if (now.isAfter(issuedAt.plusSeconds(envelope.ttlSeconds()))) {
            return VerifyResult.error("referral payload has expired");
        }
        ReferralSigningKey key = keysById.get(envelope.keyId());
        if (key == null) {
            return VerifyResult.error("referral keyId not recognized");
        }
        if (!key.isValidFor(issuedAt)) {
            return VerifyResult.error("referral keyId is outside its validity window");
        }
        BackendTarget target = routingService.findBackendById(envelope.targetBackendId(), false);
        if (target == null) {
            return VerifyResult.error("referral target backend not found");
        }
        if (!key.isAllowedForBackend(target)) {
            return VerifyResult.error("referral key scope does not allow backend");
        }
        String expectedSignature = sign(canonical(envelope), key.secret);
        if (!constantTimeEquals(expectedSignature, envelope.signature())) {
            return VerifyResult.error("referral signature mismatch");
        }
        String nonceKey = key.keyId + ":" + envelope.nonce();
        if (!nonceCache.register(nonceKey, issuedAt)) {
            return VerifyResult.error("referral nonce replayed");
        }
        return VerifyResult.ok(target.id());
    }

    private Map<String, ReferralSigningKey> buildKeys(HyproxConfig.SigningConfig signing) {
        Map<String, ReferralSigningKey> keys = new HashMap<>();
        if (signing.keys == null) {
            return keys;
        }
        for (HyproxConfig.SigningKeyConfig key : signing.keys) {
            if (key == null || isBlank(key.keyId) || isBlank(key.key)) {
                continue;
            }
            Instant validFrom = parseInstant(key.validFrom);
            Instant validTo = parseInstant(key.validTo);
            ReferralSigningKey signingKey = new ReferralSigningKey(
                    key.keyId,
                    key.key,
                    key.scope,
                    key.scopeId,
                    validFrom,
                    validTo
            );
            keys.put(signingKey.keyId, signingKey);
        }
        return keys;
    }

    private Instant parseInstant(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new ConfigException("Invalid signing key validity timestamp: " + value, e);
        }
    }

    private String canonical(ReferralEnvelope envelope) {
        return nullToEmpty(envelope.keyId())
                + "\n" + envelope.issuedAt()
                + "\n" + envelope.ttlSeconds()
                + "\n" + nullToEmpty(envelope.nonce())
                + "\n" + nullToEmpty(envelope.targetBackendId())
                + "\n" + nullToEmpty(envelope.clientUuid());
    }

    private String sign(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute referral signature", e);
        }
    }

    private byte[] encode(ReferralEnvelope envelope) {
        try {
            return MAPPER.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private boolean hasExpectedNonceSize(String nonce) {
        byte[] decoded = decodeBase64(nonce);
        return decoded != null && decoded.length >= defaultNonceBytes;
    }

    private byte[] decodeBase64(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            try {
                return Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException second) {
                return null;
            }
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Generator for referral nonces.
     */
    public interface NonceGenerator {
        String nextNonce(int bytes);
    }

    private static final class SecureNonceGenerator implements NonceGenerator {
        private final SecureRandom random = new SecureRandom();

        @Override
        public String nextNonce(int bytes) {
            byte[] buffer = new byte[Math.max(bytes, 1)];
            random.nextBytes(buffer);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        }
    }

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public static final class SignResult {
        private final boolean ok;
        private final byte[] payload;
        private final String error;

        public static SignResult ok(byte[] payload) {
            return new SignResult(true, payload, null);
        }

        public static SignResult error(String message) {
            return new SignResult(false, null, message);
        }
    }

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public static final class VerifyResult {
        private final boolean ok;
        private final String targetBackendId;
        private final String error;

        public static VerifyResult ok(String targetBackendId) {
            return new VerifyResult(true, targetBackendId, null);
        }

        public static VerifyResult empty() {
            return new VerifyResult(false, null, null);
        }

        public static VerifyResult error(String message) {
            return new VerifyResult(false, null, message);
        }
    }

    private static final class ReferralSigningKey {
        private final String keyId;
        private final String secret;
        private final String scope;
        private final String scopeId;
        private final Instant validFrom;
        private final Instant validTo;

        private ReferralSigningKey(String keyId,
                                   String secret,
                                   String scope,
                                   String scopeId,
                                   Instant validFrom,
                                   Instant validTo) {
            this.keyId = keyId;
            this.secret = secret;
            this.scope = scope;
            this.scopeId = scopeId;
            this.validFrom = validFrom;
            this.validTo = validTo;
        }

        private boolean isValidFor(Instant instant) {
            if (instant == null) {
                return false;
            }
            if (validFrom != null && instant.isBefore(validFrom)) {
                return false;
            }
            return validTo == null || !instant.isAfter(validTo);
        }

        private boolean isAllowedForBackend(BackendTarget backend) {
            if (backend == null) {
                return false;
            }
            if ("backend".equalsIgnoreCase(scope)) {
                return backend.id().equals(scopeId);
            }
            if ("pool".equalsIgnoreCase(scope)) {
                return backend.pool().equals(scopeId);
            }
            return "global".equalsIgnoreCase(scope);
        }
    }
}
