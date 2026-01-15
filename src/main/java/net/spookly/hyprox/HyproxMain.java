package net.spookly.hyprox;

import net.spookly.hyprox.config.ConfigLoader;
import net.spookly.hyprox.config.HyproxConfig;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class HyproxMain {
    private static final String DEFAULT_CONFIG = "config/hyprox.yaml";

    private HyproxMain() {
    }

    public static void main(String[] args) {
        Path configPath = resolveConfigPath(args);
        HyproxConfig config = ConfigLoader.load(configPath);
        System.out.println("Hyprox config loaded: mode=" + config.proxy.mode
                + " listen=" + config.proxy.listen.host + ":" + config.proxy.listen.port);
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
