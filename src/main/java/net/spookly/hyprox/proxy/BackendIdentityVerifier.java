package net.spookly.hyprox.proxy;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.netty.handler.codec.quic.QuicChannel;

/**
 * Validates backend certificates against host and SAN allowlists.
 */
public final class BackendIdentityVerifier {
    private BackendIdentityVerifier() {
    }

    public static VerificationResult verify(QuicChannel channel, String backendHost, List<String> allowlist) {
        Objects.requireNonNull(channel, "channel");
        X509Certificate certificate = extractCertificate(channel);
        if (certificate == null) {
            return VerificationResult.failed("backend certificate missing");
        }
        return verifyCertificate(certificate, backendHost, allowlist);
    }

    public static VerificationResult verifyCertificate(X509Certificate certificate,
                                                       String backendHost,
                                                       List<String> allowlist) {
        if (certificate == null) {
            return VerificationResult.failed("backend certificate missing");
        }
        if (isBlank(backendHost)) {
            return VerificationResult.failed("backend host missing");
        }
        BackendIdentity identity = BackendIdentity.fromCertificate(certificate);
        if (!identity.matchesHost(backendHost)) {
            return VerificationResult.failed("backend certificate host mismatch");
        }
        if (!identity.matchesAllowlist(allowlist, backendHost)) {
            return VerificationResult.failed("backend certificate not allowed");
        }
        return VerificationResult.success();
    }

    private static X509Certificate extractCertificate(QuicChannel channel) {
        SSLEngine engine = channel.sslEngine();
        if (engine == null) {
            return null;
        }
        SSLSession session = engine.getSession();
        if (session == null) {
            return null;
        }
        try {
            Certificate[] certificates = session.getPeerCertificates();
            if (certificates == null || certificates.length == 0) {
                return null;
            }
            for (Certificate certificate : certificates) {
                if (certificate instanceof X509Certificate) {
                    return (X509Certificate) certificate;
                }
            }
            return null;
        } catch (SSLPeerUnverifiedException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class BackendIdentity {
        private final List<String> dnsNames;
        private final List<String> ipAddresses;
        private final String commonName;
        private final boolean hasSans;

        private BackendIdentity(List<String> dnsNames, List<String> ipAddresses, String commonName, boolean hasSans) {
            this.dnsNames = dnsNames;
            this.ipAddresses = ipAddresses;
            this.commonName = commonName;
            this.hasSans = hasSans;
        }

        static BackendIdentity fromCertificate(X509Certificate certificate) {
            List<String> dnsNames = new ArrayList<>();
            List<String> ipAddresses = new ArrayList<>();
            boolean hasSans = false;
            try {
                Collection<List<?>> subjectAltNames = certificate.getSubjectAlternativeNames();
                if (subjectAltNames != null) {
                    for (List<?> entry : subjectAltNames) {
                        if (entry == null || entry.size() < 2) {
                            continue;
                        }
                        Integer type = entry.get(0) instanceof Integer ? (Integer) entry.get(0) : null;
                        Object value = entry.get(1);
                        if (type == null || value == null) {
                            continue;
                        }
                        hasSans = true;
                        if (type == 2 && value instanceof String) {
                            dnsNames.add((String) value);
                        } else if (type == 7 && value instanceof String) {
                            ipAddresses.add((String) value);
                        }
                    }
                }
            } catch (Exception ignored) {
                hasSans = false;
            }
            String commonName = parseCommonName(certificate);
            return new BackendIdentity(normalizeList(dnsNames), normalizeList(ipAddresses), commonName, hasSans);
        }

        boolean matchesHost(String backendHost) {
            String normalizedHost = backendHost.trim();
            if (isIpAddress(normalizedHost)) {
                return ipAddresses.contains(normalizedHost);
            }
            List<String> candidates = dnsCandidates();
            for (String candidate : candidates) {
                if (matchesDns(normalizedHost, candidate)) {
                    return true;
                }
            }
            return false;
        }

        boolean matchesAllowlist(List<String> allowlist, String backendHost) {
            if (allowlist == null || allowlist.isEmpty()) {
                return true;
            }
            List<String> candidates = new ArrayList<>();
            candidates.addAll(dnsCandidates());
            candidates.addAll(ipAddresses);
            candidates.add(backendHost.trim());
            for (String candidate : candidates) {
                if (candidate == null || candidate.isEmpty()) {
                    continue;
                }
                for (String entry : allowlist) {
                    if (entry == null || entry.trim().isEmpty()) {
                        continue;
                    }
                    if (matchesAllowlistEntry(candidate, entry)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private List<String> dnsCandidates() {
            if (hasSans && !dnsNames.isEmpty()) {
                return dnsNames;
            }
            if (!hasSans && commonName != null && !commonName.isEmpty()) {
                return Collections.singletonList(commonName);
            }
            return Collections.emptyList();
        }

        private static List<String> normalizeList(List<String> values) {
            List<String> normalized = new ArrayList<>();
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
            return normalized;
        }

        private static String parseCommonName(X509Certificate certificate) {
            if (certificate.getSubjectX500Principal() == null) {
                return null;
            }
            String name = certificate.getSubjectX500Principal().getName();
            if (name == null || name.isEmpty()) {
                return null;
            }
            for (String part : name.split(",")) {
                String trimmed = part.trim();
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("CN=")) {
                    return trimmed.substring(3).trim();
                }
            }
            return null;
        }

        private static boolean isIpAddress(String host) {
            if (host.contains(":")) {
                return true;
            }
            String[] parts = host.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            for (String part : parts) {
                if (part.isEmpty()) {
                    return false;
                }
                try {
                    int value = Integer.parseInt(part);
                    if (value < 0 || value > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }

        private static boolean matchesDns(String host, String candidate) {
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
            if (normalizedCandidate.startsWith("*.")) {
                String suffix = normalizedCandidate.substring(1);
                return normalizedHost.endsWith(suffix) && normalizedHost.length() > suffix.length();
            }
            return normalizedHost.equals(normalizedCandidate);
        }

        private static boolean matchesAllowlistEntry(String candidate, String entry) {
            String normalizedEntry = entry.trim().toLowerCase(Locale.ROOT);
            String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
            if (normalizedEntry.startsWith("*.") && !isIpAddress(normalizedCandidate)) {
                String suffix = normalizedEntry.substring(1);
                return normalizedCandidate.endsWith(suffix) && normalizedCandidate.length() > suffix.length();
            }
            return normalizedCandidate.equals(normalizedEntry);
        }
    }

    public static final class VerificationResult {
        private final boolean ok;
        private final String reason;

        private VerificationResult(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }

        public static VerificationResult success() {
            return new VerificationResult(true, null);
        }

        public static VerificationResult failed(String reason) {
            return new VerificationResult(false, reason);
        }

        public boolean ok() {
            return ok;
        }

        public boolean isOk() {
            return ok;
        }

        public String reason() {
            return reason;
        }
    }
}
