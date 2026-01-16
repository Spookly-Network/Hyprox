package net.spookly.hyprox.proxy;

import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;

/**
 * Captures per-session metadata derived from the QUIC handshake.
 */
@Getter
@Accessors(fluent = true)
public final class ProxySessionContext {
    public static final AttributeKey<ProxySessionContext> SESSION_CONTEXT =
            AttributeKey.valueOf("hyprox.session.context");

    private final String remoteAddress;
    private final String clientCertificateSubject;
    private final String clientCertificateSha256;
    private final X509Certificate clientCertificate;

    private ProxySessionContext(String remoteAddress,
                                String clientCertificateSubject,
                                String clientCertificateSha256,
                                X509Certificate clientCertificate) {
        this.remoteAddress = remoteAddress;
        this.clientCertificateSubject = clientCertificateSubject;
        this.clientCertificateSha256 = clientCertificateSha256;
        this.clientCertificate = clientCertificate;
    }

    /**
     * Build a session context for the provided peer certificate.
     */
    public static ProxySessionContext from(String remoteAddress, X509Certificate certificate) {
        if (certificate == null) {
            return new ProxySessionContext(remoteAddress, null, null, null);
        }
        String subject = certificate.getSubjectX500Principal() == null
                ? null
                : certificate.getSubjectX500Principal().getName();
        String fingerprint = fingerprintSha256(certificate);
        return new ProxySessionContext(remoteAddress, subject, fingerprint, certificate);
    }

    private static String fingerprintSha256(X509Certificate certificate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(certificate.getEncoded());
            return toHex(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
