package net.spookly.hyprox.migration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Signed migration ticket issued by a backend to authorize handoff.
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MigrationTicket {
    /** Signing key id used to verify the ticket. */
    private String keyId;
    /** Issuance time in epoch seconds. */
    private long issuedAt;
    /** Ticket validity window in seconds. */
    private int ttlSeconds;
    /** Base64url nonce to prevent replay. */
    private String nonce;
    /** Backend id that issued the ticket. */
    private String sourceBackendId;
    /** Backend id the client is migrating to. */
    private String targetBackendId;
    /** Client UUID bound to this ticket. */
    private String clientUuid;
    /** HMAC signature over the canonical payload fields. */
    private String signature;
}
