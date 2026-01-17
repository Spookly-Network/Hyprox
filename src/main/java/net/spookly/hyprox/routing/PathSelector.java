package net.spookly.hyprox.routing;

import java.util.List;
import java.util.Objects;

import net.spookly.hyprox.config.HyproxConfig;

/**
 * Selects redirect vs full proxy data path based on config and pool.
 */
public final class PathSelector {
    private final HyproxConfig config;

    public PathSelector(HyproxConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Determine the data path for the given pool.
     */
    public DataPath select(String pool) {
        HyproxConfig.ProxyConfig proxy = config.proxy;
        if (proxy == null) {
            return DataPath.REDIRECT;
        }
        ProxyMode mode = ProxyMode.fromConfig(proxy.mode);
        switch (mode) {
            case FULL:
                return DataPath.FULL_PROXY;
            case REDIRECT:
                return DataPath.REDIRECT;
            case HYBRID:
            default:
                if (isPoolInFullProxy(pool, proxy.fullProxyPools)) {
                    return DataPath.FULL_PROXY;
                }
                if ("full".equalsIgnoreCase(proxy.defaultPath)) {
                    return DataPath.FULL_PROXY;
                }
                return DataPath.REDIRECT;
        }
    }

    private boolean isPoolInFullProxy(String pool, List<String> fullProxyPools) {
        if (pool == null || fullProxyPools == null || fullProxyPools.isEmpty()) {
            return false;
        }
        for (String entry : fullProxyPools) {
            if (entry != null && entry.equalsIgnoreCase(pool)) {
                return true;
            }
        }
        return false;
    }
}
