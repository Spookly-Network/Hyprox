package net.spookly.hyprox.agent;

import java.util.concurrent.CompletableFuture;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

/**
 * Command that describes the current Hyprox agent status.
 */
public final class HyproxAgentCommand extends AbstractCommand {
    /**
     * Command name used to access the Hyprox agent stub.
     */
    public static final String COMMAND_NAME = "hyproxagent";

    /**
     * Command description shown in help output.
     */
    public static final String COMMAND_DESCRIPTION = "Show Hyprox agent status and next steps";

    public HyproxAgentCommand() {
        super(COMMAND_NAME, COMMAND_DESCRIPTION);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw("Hyprox agent is a stub. It does not exchange certs or sign referrals."));
        context.sendMessage(Message.raw("Configure certs on the proxy and backends, then use Hyprox for routing."));
        context.sendMessage(Message.raw("Planned: referral signing, backend metadata, and in-game transfer commands."));
        return CompletableFuture.completedFuture(null);
    }
}
