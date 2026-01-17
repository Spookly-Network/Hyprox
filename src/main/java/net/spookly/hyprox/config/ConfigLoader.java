package net.spookly.hyprox.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

public final class ConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private ConfigLoader() {
    }

    /**
     * Load and validate the Hyprox YAML configuration.
     */
    public static HyproxConfig load(Path path) {
        if (path == null) {
            throw new ConfigException("Config path is required");
        }
        if (!Files.exists(path)) {
            writeDefaultConfig(path);
            throw new ConfigException("Config file did not exist, generated default at: " + path);
        }
        Object raw;
        Yaml yaml = new Yaml();
        try (Reader reader = Files.newBufferedReader(path)) {
            raw = yaml.load(reader);
        } catch (IOException e) {
            throw new ConfigException("Failed to read config: " + path, e);
        }
        if (raw == null) {
            throw new ConfigException("Config file is empty: " + path);
        }
        Object expanded = EnvExpander.expand(raw, path.getParent());
        HyproxConfig config;
        try {
            config = MAPPER.convertValue(expanded, HyproxConfig.class);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Failed to parse config: " + path, e);
        }
        ConfigValidator.validate(config);
        return config;
    }

    private static final String DEFAULT_REFERRAL_HMAC_PATH = "secret/referral_hmac";
    private static final int DEFAULT_REFERRAL_HMAC_BYTES = 32;

    private static void writeDefaultConfig(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ensureReferralHmac(resolveRelativePath(parent, DEFAULT_REFERRAL_HMAC_PATH));
            Files.writeString(
                    path,
                    ConfigDefaults.defaultYaml(DEFAULT_REFERRAL_HMAC_PATH),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
        } catch (IOException e) {
            throw new ConfigException("Failed to write default config: " + path, e);
        }
    }

    private static void ensureReferralHmac(Path secretPath) throws IOException {
        if (Files.exists(secretPath)) {
            return;
        }
        Path parent = secretPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String secret = generateReferralHmac();
        Files.writeString(secretPath, secret, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        setOwnerOnlyPermissions(secretPath);
    }

    private static String generateReferralHmac() {
        // One-time secret for referral signing when bootstrapping a new config.
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[DEFAULT_REFERRAL_HMAC_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void setOwnerOnlyPermissions(Path secretPath) {
        try {
            if (Files.getFileAttributeView(secretPath, java.nio.file.attribute.PosixFileAttributeView.class) == null) {
                return;
            }
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(secretPath, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Best-effort only.
        }
    }

    private static Path resolveRelativePath(Path baseDir, String rawValue) {
        Path relative = Path.of(rawValue);
        if (baseDir != null && !relative.isAbsolute()) {
            return baseDir.resolve(relative).normalize();
        }
        return relative;
    }
}
