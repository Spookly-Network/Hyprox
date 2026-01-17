package net.spookly.hyprox.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;

class ProxySessionContextTest {
    @Test
    void capturesSubjectAndFingerprint() {
        byte[] encoded = "dummy-cert".getBytes(StandardCharsets.UTF_8);
        X509Certificate certificate = new StubCertificate(encoded, "CN=client-1");
        ProxySessionContext context = ProxySessionContext.from("10.0.0.1", certificate);

        assertEquals("10.0.0.1", context.remoteAddress());
        assertEquals("CN=client-1", context.clientCertificateSubject());
        assertEquals(expectedFingerprint(encoded), context.clientCertificateSha256());
        assertNotNull(context.clientCertificate());
    }

    private static String expectedFingerprint(byte[] encoded) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(encoded);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class StubCertificate extends X509Certificate {
        private final byte[] encoded;
        private final X500Principal subject;

        private StubCertificate(byte[] encoded, String subject) {
            this.encoded = encoded;
            this.subject = new X500Principal(subject);
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return encoded;
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return subject;
        }

        @Override
        public void checkValidity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkValidity(Date date) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getVersion() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigInteger getSerialNumber() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Principal getIssuerDN() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Principal getSubjectDN() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date getNotBefore() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date getNotAfter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getTBSCertificate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getSignature() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getSigAlgName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getSigAlgOID() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getSigAlgParams() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean[] getKeyUsage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getBasicConstraints() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Set<String> getNonCriticalExtensionOIDs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Set<String> getCriticalExtensionOIDs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(PublicKey key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "StubCertificate";
        }

        @Override
        public PublicKey getPublicKey() {
            throw new UnsupportedOperationException();
        }
    }
}
