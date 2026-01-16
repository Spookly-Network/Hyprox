package net.spookly.hyprox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

/**
 * Renders the effective configuration with sensitive values redacted.
 */
public final class ConfigPrinter {
    private static final String REDACTED = "<redacted>";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigPrinter() {
    }

    public static String toYaml(HyproxConfig config) {
        Map<String, Object> data = MAPPER.convertValue(config, Map.class);
        redactSensitiveValues(data);
        Yaml yaml = new Yaml();
        return yaml.dump(data);
    }

    @SuppressWarnings("unchecked")
    private static void redactSensitiveValues(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object auth = data.get("auth");
        if (auth instanceof Map) {
            Map<String, Object> authMap = (Map<String, Object>) auth;
            Object referral = authMap.get("referral");
            if (referral instanceof Map) {
                Map<String, Object> referralMap = (Map<String, Object>) referral;
                Object signing = referralMap.get("signing");
                if (signing instanceof Map) {
                    Map<String, Object> signingMap = (Map<String, Object>) signing;
                    Object keys = signingMap.get("keys");
                    if (keys instanceof List) {
                        for (Object keyEntry : (List<?>) keys) {
                            if (keyEntry instanceof Map) {
                                Map<String, Object> keyMap = (Map<String, Object>) keyEntry;
                                if (keyMap.containsKey("key")) {
                                    keyMap.put("key", REDACTED);
                                }
                            }
                        }
                    }
                }
            }
        }
        Object registry = data.get("registry");
        if (registry instanceof Map) {
            Map<String, Object> registryMap = (Map<String, Object>) registry;
            Object registryAuth = registryMap.get("auth");
            if (registryAuth instanceof Map) {
                Map<String, Object> registryAuthMap = (Map<String, Object>) registryAuth;
                if (registryAuthMap.containsKey("sharedKey")) {
                    registryAuthMap.put("sharedKey", REDACTED);
                }
            }
        }
        Object agent = data.get("agent");
        if (agent instanceof Map) {
            Map<String, Object> agentMap = (Map<String, Object>) agent;
            Object agentAuth = agentMap.get("auth");
            if (agentAuth instanceof Map) {
                Map<String, Object> agentAuthMap = (Map<String, Object>) agentAuth;
                if (agentAuthMap.containsKey("sharedKey")) {
                    agentAuthMap.put("sharedKey", REDACTED);
                }
            }
        }
    }
}
