package net.spookly.hyprox.routing;

/**
 * Pool selection policy for backend routing.
 */
public enum PoolPolicy {
    WEIGHTED("weighted"),
    ROUND_ROBIN("round_robin");

    private final String configValue;

    PoolPolicy(String configValue) {
        this.configValue = configValue;
    }

    public static PoolPolicy fromConfig(String value) {
        if (value == null) {
            return WEIGHTED;
        }
        for (PoolPolicy policy : values()) {
            if (policy.configValue.equalsIgnoreCase(value)) {
                return policy;
            }
        }
        return WEIGHTED;
    }
}
