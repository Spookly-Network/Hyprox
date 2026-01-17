package net.spookly.hyprox.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import net.spookly.hyprox.config.HyproxConfig;
import org.junit.jupiter.api.Test;

class PathSelectorTest {
    @Test
    void hybridDefaultsToRedirectUnlessPoolIsForced() {
        HyproxConfig config = new HyproxConfig();
        config.proxy = proxy("hybrid", "redirect", List.of("game"));

        PathSelector selector = new PathSelector(config);

        assertEquals(DataPath.FULL_PROXY, selector.select("game"));
        assertEquals(DataPath.REDIRECT, selector.select("lobby"));
    }

    @Test
    void hybridUsesDefaultPathWhenNoForceList() {
        HyproxConfig config = new HyproxConfig();
        config.proxy = proxy("hybrid", "full", null);

        PathSelector selector = new PathSelector(config);

        assertEquals(DataPath.FULL_PROXY, selector.select("lobby"));
    }

    @Test
    void fullModeAlwaysUsesFullProxy() {
        HyproxConfig config = new HyproxConfig();
        config.proxy = proxy("full", "redirect", List.of("lobby"));

        PathSelector selector = new PathSelector(config);

        assertEquals(DataPath.FULL_PROXY, selector.select("lobby"));
        assertEquals(DataPath.FULL_PROXY, selector.select("game"));
    }

    @Test
    void redirectModeAlwaysUsesRedirect() {
        HyproxConfig config = new HyproxConfig();
        config.proxy = proxy("redirect", "full", List.of("game"));

        PathSelector selector = new PathSelector(config);

        assertEquals(DataPath.REDIRECT, selector.select("game"));
        assertEquals(DataPath.REDIRECT, selector.select("lobby"));
    }

    private HyproxConfig.ProxyConfig proxy(String mode, String defaultPath, List<String> fullProxyPools) {
        HyproxConfig.ProxyConfig proxy = new HyproxConfig.ProxyConfig();
        proxy.mode = mode;
        proxy.defaultPath = defaultPath;
        proxy.fullProxyPools = fullProxyPools;
        return proxy;
    }
}
