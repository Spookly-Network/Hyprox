package net.spookly.hyprox;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import net.spookly.hyprox.auth.ReferralService;
import net.spookly.hyprox.config.ConfigLoader;
import net.spookly.hyprox.config.ConfigPrinter;
import net.spookly.hyprox.config.ConfigWarnings;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.proxy.ProxyServer;
import net.spookly.hyprox.proxy.QuicBackendHealthProbe;
import net.spookly.hyprox.registry.BackendRegistry;
import net.spookly.hyprox.registry.RegistryAuditLogger;
import net.spookly.hyprox.registry.RegistryEventListener;
import net.spookly.hyprox.registry.RegistryServer;
import net.spookly.hyprox.routing.BackendCapacityTracker;
import net.spookly.hyprox.routing.BackendHealthProbeService;
import net.spookly.hyprox.routing.BackendHealthTracker;
import net.spookly.hyprox.routing.PathSelector;
import net.spookly.hyprox.routing.RoutingPlanner;
import net.spookly.hyprox.routing.RoutingService;

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
        CliOptions options = parseArgs(args);
        Path configPath = options.configPath;
        HyproxConfig config = ConfigLoader.load(configPath);
        emitWarnings(config, configPath);
        if (options.printEffectiveConfig) {
            System.out.println(ConfigPrinter.toYaml(config));
            return;
        }
        if (options.dryRun) {
            System.out.println("Config OK (--dry-run).");
            return;
        }
        System.out.println("Hyprox config loaded: mode=" + config.proxy.mode
                + " listen=" + config.proxy.listen.host + ":" + config.proxy.listen.port);

        RegistryEventListener eventListener = RegistryEventListener.NOOP;
        if (config.registry != null && Boolean.TRUE.equals(config.registry.enabled)) {
            eventListener = RegistryAuditLogger.INSTANCE;
        }
        BackendRegistry registry = BackendRegistry.fromConfig(config, eventListener);
        BackendCapacityTracker capacityTracker = new BackendCapacityTracker();
        BackendHealthTracker healthTracker = new BackendHealthTracker();
        RoutingService routingService = new RoutingService(config, registry, capacityTracker, healthTracker);
        RoutingPlanner routingPlanner = new RoutingPlanner(routingService, new PathSelector(config));
        ReferralService referralService = new ReferralService(config, routingService);
        ProxyServer proxyServer = new ProxyServer(config, routingPlanner, referralService);
        proxyServer.start();

        BackendHealthProbeService healthProbeService = null;
        if (config.routing != null && config.routing.health != null) {
            healthProbeService = new BackendHealthProbeService(
                    config,
                    routingService,
                    healthTracker,
                    new QuicBackendHealthProbe(config)
            );
            healthProbeService.start();
        }

        RegistryServer registryServer = null;
        if (config.registry != null && Boolean.TRUE.equals(config.registry.enabled)) {
            registryServer = new RegistryServer(config, registry);
            registryServer.start();
        }

        CountDownLatch latch = new CountDownLatch(1);
        RegistryServer finalRegistryServer = registryServer;
        BackendHealthProbeService finalHealthProbeService = healthProbeService;
        ProxyServer finalProxyServer = proxyServer;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (finalHealthProbeService != null) {
                finalHealthProbeService.stop();
            }
            if (finalRegistryServer != null) {
                finalRegistryServer.stop();
            }
            if (finalProxyServer != null) {
                finalProxyServer.stop();
            }
            latch.countDown();
        }));

        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static CliOptions parseArgs(String[] args) {
        Path configPath = Paths.get(DEFAULT_CONFIG);
        boolean dryRun = false;
        boolean printEffectiveConfig = false;
        if (args == null) {
            return new CliOptions(configPath, dryRun, printEffectiveConfig);
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--config".equals(arg) || "-c".equals(arg)) {
                if (i + 1 < args.length) {
                    configPath = Paths.get(args[++i]);
                    continue;
                }
            }
            if ("--dry-run".equals(arg)) {
                dryRun = true;
                continue;
            }
            if ("--print-effective-config".equals(arg)) {
                printEffectiveConfig = true;
            }
        }
        return new CliOptions(configPath, dryRun, printEffectiveConfig);
    }

    private static void emitWarnings(HyproxConfig config, Path configPath) {
        for (String warning : ConfigWarnings.collect(config, configPath)) {
            System.err.println("Config warning: " + warning);
        }
    }

    private record CliOptions(Path configPath, boolean dryRun, boolean printEffectiveConfig) {
    }
}
