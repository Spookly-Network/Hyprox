package net.spookly.hyprox.registry;

import net.spookly.hyprox.config.HyproxConfig;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility helpers for registry initialization.
 */
final class RegistryUtils {
    private RegistryUtils() {
    }

    static Set<String> collectStaticBackendIds(HyproxConfig config) {
        Set<String> ids = new HashSet<>();
        if (config == null || config.routing == null || config.routing.pools == null) {
            return ids;
        }
        for (Map.Entry<String, HyproxConfig.PoolConfig> entry : config.routing.pools.entrySet()) {
            HyproxConfig.PoolConfig pool = entry.getValue();
            if (pool == null || pool.backends == null) {
                continue;
            }
            for (HyproxConfig.BackendConfig backend : pool.backends) {
                if (backend != null && backend.id != null) {
                    ids.add(backend.id);
                }
            }
        }
        return ids;
    }
}
