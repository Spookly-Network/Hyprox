package net.spookly.hyprox.migration;

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
 * Signs and verifies migration tickets used during seamless handoff.
 */
public final class MigrationTicketService {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RoutingService routingService;
    private final Clock clock;
    private final NonceCache nonceCache;
    private final NonceGenerator nonceGenerator;
    private final int maxTtlSeconds;
    private final int defaultTtlSeconds;
    private final int nonceBytes;
    private final String activeKeyId;
    private final Map<String, MigrationSigningKey> keysById;

    public MigrationTicketService(HyproxConfig config, RoutingService routingService) {
        this(config, routingService, Clock.systemUTC(), new SecureNonceGenerator());
    }

    MigrationTicketService(HyproxConfig config,
                           RoutingService routingService,
                           Clock clock,
                           NonceGenerator nonceGenerator) {
        if (config == null) {
            throw new ConfigException("Config is required for migration ticket signing");
        }
        HyproxConfig.MigrationConfig migration = config.migration;
        if (migration == null) {
            throw new ConfigException("migration is required for migration ticket signing");
        }
        HyproxConfig.SigningConfig signing = migration.ticketSigning;
        if (signing == null) {
            throw new ConfigException("migration.ticketSigning is required");
        }
        if (isBlank(signing.algorithm) || !"hmac-sha256".equalsIgnoreCase(signing.algorithm)) {
            throw new ConfigException("migration.ticketSigning.algorithm must be hmac-sha256");
        }
        if (isBlank(signing.activeKeyId)) {
            throw new ConfigException("migration.ticketSigning.activeKeyId is required");
        }
        this.routingService = routingService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.nonceGenerator = nonceGenerator == null ? new SecureNonceGenerator() : nonceGenerator;
        int configuredMaxTtl = migration.ticketMaxAgeSeconds != null ? migration.ticketMaxAgeSeconds : 30;
        int configuredTtl = signing.ttlSeconds != null ? signing.ttlSeconds : configuredMaxTtl;
        if (configuredMaxTtl <= 0 || configuredTtl <= 0) {
            throw new ConfigException("migration ticket ttlSeconds must be greater than 0");
        }
        this.maxTtlSeconds = configuredMaxTtl;
        this.defaultTtlSeconds = configuredTtl;
        this.nonceBytes = signing.nonceBytes != null ? signing.nonceBytes : 16;
        if (nonceBytes <= 0) {
            throw new ConfigException("migration.ticketSigning.nonceBytes must be greater than 0");
        }
        this.activeKeyId = signing.activeKeyId;
        this.keysById = buildKeys(signing);
        this.nonceCache = new NonceCache(Math.max(maxTtlSeconds, defaultTtlSeconds));
    }

    /**
     * Build a signed migration ticket for the target backend and client.
     */
    public SignResult signTicket(String sourceBackendId, String targetBackendId, UUID clientUuid) {
        if (isBlank(sourceBackendId)) {
            return SignResult.error("source backend id is required");
        }
        if (isBlank(targetBackendId)) {
            return SignResult.error("target backend id is required");
        }
        if (clientUuid == null) {
            return SignResult.error("client uuid is required");
        }
        BackendTarget sourceBackend = resolveBackend(sourceBackendId);
        BackendTarget targetBackend = resolveBackend(targetBackendId);
        if (routingService != null && sourceBackend == null) {
            return SignResult.error("source backend not found");
        }
        if (routingService != null && targetBackend == null) {
            return SignResult.error("target backend not found");
        }
        MigrationSigningKey key = keysById.get(activeKeyId);
        if (key == null) {
            return SignResult.error("active signing key is missing");
        }
        Instant now = clock.instant();
        if (!key.isValidFor(now)) {
            return SignResult.error("active signing key is outside its validity window");
        }
        if (!key.isAllowedForSource(sourceBackend, sourceBackendId)) {
            return SignResult.error("active signing key scope does not allow source backend");
        }
        String nonce = nonceGenerator.nextNonce(nonceBytes);
        if (isBlank(nonce)) {
            return SignResult.error("nonce generation failed");
        }
        MigrationTicket envelope = new MigrationTicket(
                key.keyId,
                now.getEpochSecond(),
                defaultTtlSeconds,
                nonce,
                sourceBackendId,
                targetBackendId,
                clientUuid.toString(),
                null
        );
        String signature = sign(canonical(envelope), key.secret);
        MigrationTicket signed = new MigrationTicket(
                envelope.keyId(),
                envelope.issuedAt(),
                envelope.ttlSeconds(),
                envelope.nonce(),
                envelope.sourceBackendId(),
                envelope.targetBackendId(),
                envelope.clientUuid(),
                signature
        );
        byte[] payload = encode(signed);
        if (payload == null) {
            return SignResult.error("failed to encode migration ticket");
        }
        return SignResult.ok(payload);
    }

    /**
     * Verify a migration ticket for the expected backends and client.
     */
    public VerifyResult verifyTicket(byte[] payload,
                                     UUID clientUuid,
                                     String expectedSourceBackendId,
                                     String expectedTargetBackendId) {
        if (payload == null || payload.length == 0) {
            return VerifyResult.empty();
        }
        if (clientUuid == null) {
            return VerifyResult.error("client uuid is required");
        }
        if (isBlank(expectedSourceBackendId)) {
            return VerifyResult.error("source backend id is required");
        }
        if (isBlank(expectedTargetBackendId)) {
            return VerifyResult.error("target backend id is required");
        }
        MigrationTicket ticket;
        try {
            ticket = MAPPER.readValue(payload, MigrationTicket.class);
        } catch (IOException e) {
            return VerifyResult.error("migration ticket is not valid JSON");
        }
        if (isBlank(ticket.keyId())
                || isBlank(ticket.nonce())
                || isBlank(ticket.sourceBackendId())
                || isBlank(ticket.targetBackendId())
                || isBlank(ticket.clientUuid())
                || isBlank(ticket.signature())
                || ticket.issuedAt() <= 0
                || ticket.ttlSeconds() <= 0) {
            return VerifyResult.error("migration ticket is missing required fields");
        }
        if (!clientUuid.toString().equals(ticket.clientUuid())) {
            return VerifyResult.error("migration ticket client uuid mismatch");
        }
        if (!expectedSourceBackendId.equals(ticket.sourceBackendId())) {
            return VerifyResult.error("migration ticket source backend mismatch");
        }
        if (!expectedTargetBackendId.equals(ticket.targetBackendId())) {
            return VerifyResult.error("migration ticket target backend mismatch");
        }
        if (!hasExpectedNonceSize(ticket.nonce())) {
            return VerifyResult.error("migration ticket nonce size is invalid");
        }
        if (ticket.ttlSeconds() > maxTtlSeconds) {
            return VerifyResult.error("migration ticket ttl exceeds configured max");
        }
        Instant issuedAt = Instant.ofEpochSecond(ticket.issuedAt());
        Instant now = clock.instant();
        if (issuedAt.isAfter(now.plusSeconds(ticket.ttlSeconds()))) {
            return VerifyResult.error("migration ticket issuedAt is too far in the future");
        }
        if (now.isAfter(issuedAt.plusSeconds(ticket.ttlSeconds()))) {
            return VerifyResult.error("migration ticket has expired");
        }
        MigrationSigningKey key = keysById.get(ticket.keyId());
        if (key == null) {
            return VerifyResult.error("migration ticket keyId not recognized");
        }
        if (!key.isValidFor(issuedAt)) {
            return VerifyResult.error("migration ticket keyId is outside its validity window");
        }
        BackendTarget sourceBackend = resolveBackend(ticket.sourceBackendId());
        BackendTarget targetBackend = resolveBackend(ticket.targetBackendId());
        if (routingService != null && sourceBackend == null) {
            return VerifyResult.error("migration ticket source backend not found");
        }
        if (routingService != null && targetBackend == null) {
            return VerifyResult.error("migration ticket target backend not found");
        }
        if (!key.isAllowedForSource(sourceBackend, ticket.sourceBackendId())) {
            return VerifyResult.error("migration ticket key scope does not allow source backend");
        }
        String expectedSignature = sign(canonical(ticket), key.secret);
        if (!constantTimeEquals(expectedSignature, ticket.signature())) {
            return VerifyResult.error("migration ticket signature mismatch");
        }
        String nonceKey = key.keyId + ":" + ticket.nonce();
        if (!nonceCache.register(nonceKey, issuedAt)) {
            return VerifyResult.error("migration ticket nonce replayed");
        }
        return VerifyResult.ok(ticket);
    }

    private BackendTarget resolveBackend(String backendId) {
        if (routingService == null || isBlank(backendId)) {
            return null;
        }
        return routingService.findBackendById(backendId, false);
    }

    private Map<String, MigrationSigningKey> buildKeys(HyproxConfig.SigningConfig signing) {
        Map<String, MigrationSigningKey> keys = new HashMap<>();
        if (signing.keys == null) {
            return keys;
        }
        for (HyproxConfig.SigningKeyConfig key : signing.keys) {
            if (key == null || isBlank(key.keyId) || isBlank(key.key)) {
                continue;
            }
            Instant validFrom = parseInstant(key.validFrom);
            Instant validTo = parseInstant(key.validTo);
            MigrationSigningKey signingKey = new MigrationSigningKey(
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
            throw new ConfigException("Invalid migration signing key validity timestamp: " + value, e);
        }
    }

    private String canonical(MigrationTicket ticket) {
        return nullToEmpty(ticket.keyId())
                + "\n" + ticket.issuedAt()
                + "\n" + ticket.ttlSeconds()
                + "\n" + nullToEmpty(ticket.nonce())
                + "\n" + nullToEmpty(ticket.sourceBackendId())
                + "\n" + nullToEmpty(ticket.targetBackendId())
                + "\n" + nullToEmpty(ticket.clientUuid());
    }

    private String sign(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute migration ticket signature", e);
        }
    }

    private byte[] encode(MigrationTicket ticket) {
        try {
            return MAPPER.writeValueAsBytes(ticket);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private boolean hasExpectedNonceSize(String nonce) {
        byte[] decoded = decodeBase64(nonce);
        return decoded != null && decoded.length >= nonceBytes;
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
     * Generator for migration ticket nonces.
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
        private final MigrationTicket ticket;
        private final String error;

        public static VerifyResult ok(MigrationTicket ticket) {
            return new VerifyResult(true, ticket, null);
        }

        public static VerifyResult empty() {
            return new VerifyResult(false, null, null);
        }

        public static VerifyResult error(String message) {
            return new VerifyResult(false, null, message);
        }
    }

    private static final class MigrationSigningKey {
        private final String keyId;
        private final String secret;
        private final String scope;
        private final String scopeId;
        private final Instant validFrom;
        private final Instant validTo;

        private MigrationSigningKey(String keyId,
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

        private boolean isAllowedForSource(BackendTarget sourceBackend, String sourceBackendId) {
            if ("backend".equalsIgnoreCase(scope)) {
                return sourceBackendId != null && sourceBackendId.equals(scopeId);
            }
            if ("pool".equalsIgnoreCase(scope)) {
                return sourceBackend != null && sourceBackend.pool().equals(scopeId);
            }
            return "global".equalsIgnoreCase(scope);
        }
    }
}
