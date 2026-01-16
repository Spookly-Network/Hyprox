package net.spookly.hyprox.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPrinterTest {
    @Test
    void redactsSensitiveValues() {
        HyproxConfig config = new HyproxConfig();
        config.auth = new HyproxConfig.AuthConfig();
        config.auth.referral = new HyproxConfig.ReferralConfig();
        config.auth.referral.signing = new HyproxConfig.SigningConfig();
        HyproxConfig.SigningKeyConfig signingKey = new HyproxConfig.SigningKeyConfig();
        signingKey.key = "referral-secret";
        config.auth.referral.signing.keys = List.of(signingKey);

        config.registry = new HyproxConfig.RegistryConfig();
        config.registry.auth = new HyproxConfig.RegistryAuthConfig();
        config.registry.auth.sharedKey = "registry-secret";

        config.agent = new HyproxConfig.AgentConfig();
        config.agent.auth = new HyproxConfig.AgentAuthConfig();
        config.agent.auth.sharedKey = "agent-secret";

        String yaml = ConfigPrinter.toYaml(config);
        assertFalse(yaml.contains("referral-secret"));
        assertFalse(yaml.contains("registry-secret"));
        assertFalse(yaml.contains("agent-secret"));
        assertTrue(yaml.contains("<redacted>"));
    }
}
