package net.spookly.hyprox.auth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Signed referral payload sent in {@code ClientReferral.data} and echoed in {@code Connect.referralData}.
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ReferralEnvelope {
    private String keyId;
    private long issuedAt;
    private int ttlSeconds;
    private String nonce;
    private String targetBackendId;
    private String clientUuid;
    private String signature;
}
