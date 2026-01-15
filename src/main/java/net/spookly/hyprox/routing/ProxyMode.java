package net.spookly.hyprox.routing;

/**
 * Proxy operating mode mapping to config values.
 */
public enum ProxyMode {
    HYBRID("hybrid"),
    REDIRECT("redirect"),
    FULL("full");

    private final String configValue;

    ProxyMode(String configValue) {
        this.configValue = configValue;
    }

    public static ProxyMode fromConfig(String value) {
        if (value == null) {
            return HYBRID;
        }
        for (ProxyMode mode : values()) {
            if (mode.configValue.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return HYBRID;
    }
}
