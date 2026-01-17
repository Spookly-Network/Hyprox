package net.spookly.hyprox.agent;

import java.util.logging.Level;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/**
 * Optional server-side Hyprox agent stub.
 *
 * <p>Registers basic commands and serves as a placeholder for future
 * referral signing and backend metadata publishing. It does not handle
 * certificate exchange.
 */
public final class HyproxAgentPlugin extends JavaPlugin {
    /**
     * Create the plugin instance.
     */
    public HyproxAgentPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getCommandRegistry().registerCommand(new HyproxAgentCommand());
        getLogger().at(Level.INFO).log("Hyprox agent loaded (stub). No cert exchange or referral signing is active.");
    }
}
