package net.spookly.hyprox.proxy;

import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendIdentityVerifierTest {
    @Test
    void allowsMatchingDnsSan() {
        X509Certificate certificate = new StubCertificate(
                List.of(List.of(2, "backend.local")),
                "CN=unused"
        );

        BackendIdentityVerifier.VerificationResult result = BackendIdentityVerifier.verifyCertificate(
                certificate,
                "backend.local",
                List.of()
        );

        assertTrue(result.isOk());
    }

    @Test
    void rejectsMismatchedHost() {
        X509Certificate certificate = new StubCertificate(
                List.of(List.of(2, "backend.local")),
                "CN=unused"
        );

        BackendIdentityVerifier.VerificationResult result = BackendIdentityVerifier.verifyCertificate(
                certificate,
                "other.local",
                List.of()
        );

        assertFalse(result.isOk());
    }

    @Test
    void usesCommonNameWhenNoSans() {
        X509Certificate certificate = new StubCertificate(null, "CN=backend.local");

        BackendIdentityVerifier.VerificationResult result = BackendIdentityVerifier.verifyCertificate(
                certificate,
                "backend.local",
                List.of()
        );

        assertTrue(result.isOk());
    }

    @Test
    void enforcesAllowlist() {
        X509Certificate certificate = new StubCertificate(
                List.of(List.of(2, "backend.local")),
                "CN=unused"
        );

        BackendIdentityVerifier.VerificationResult result = BackendIdentityVerifier.verifyCertificate(
                certificate,
                "backend.local",
                List.of("*.example.net")
        );

        assertFalse(result.isOk());
    }

    private static final class StubCertificate extends X509Certificate {
        private final Collection<List<?>> subjectAltNames;
        private final X500Principal subject;

        private StubCertificate(Collection<List<?>> subjectAltNames, String subject) {
            this.subjectAltNames = subjectAltNames;
            this.subject = new X500Principal(subject);
        }

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() {
            return subjectAltNames;
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
        public byte[] getEncoded() throws CertificateEncodingException {
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
    }
}
