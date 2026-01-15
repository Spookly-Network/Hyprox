package net.spookly.hyprox;

import net.spookly.hyprox.config.ConfigLoader;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.registry.BackendRegistry;
import net.spookly.hyprox.registry.RegistryServer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

/**
 * Standalone entry point for the Hyprox proxy process.
 */
public final class HyproxMain {
    private static final String DEFAULT_CONFIG = "config/hyprox.yaml";

    private HyproxMain() {
    }

    /**
     * Boot the proxy process and optional registry service.
     */
    public static void main(String[] args) {
        Path configPath = resolveConfigPath(args);
        HyproxConfig config = ConfigLoader.load(configPath);
        System.out.println("Hyprox config loaded: mode=" + config.proxy.mode
                + " listen=" + config.proxy.listen.host + ":" + config.proxy.listen.port);

        RegistryServer registryServer = null;
        if (config.registry != null && Boolean.TRUE.equals(config.registry.enabled)) {
            BackendRegistry registry = BackendRegistry.fromConfig(config);
            registryServer = new RegistryServer(config, registry);
            registryServer.start();
        }

        CountDownLatch latch = new CountDownLatch(1);
        RegistryServer finalRegistryServer = registryServer;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (finalRegistryServer != null) {
                finalRegistryServer.stop();
            }
            latch.countDown();
        }));

        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static Path resolveConfigPath(String[] args) {
        if (args == null) {
            return Paths.get(DEFAULT_CONFIG);
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--config".equals(arg) || "-c".equals(arg)) {
                if (i + 1 < args.length) {
                    return Paths.get(args[i + 1]);
                }
            }
        }
        return Paths.get(DEFAULT_CONFIG);
    }
}
