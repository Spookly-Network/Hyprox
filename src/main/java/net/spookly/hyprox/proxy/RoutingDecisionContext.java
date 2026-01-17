package net.spookly.hyprox.proxy;

import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.spookly.hyprox.routing.BackendTarget;
import net.spookly.hyprox.routing.DataPath;
import net.spookly.hyprox.routing.RoutingDecision;

/**
 * Captures routing outcomes for logging and metrics.
 */
@Getter
@Accessors(fluent = true)
public final class RoutingDecisionContext {
    public static final AttributeKey<RoutingDecisionContext> ROUTING_CONTEXT =
            AttributeKey.valueOf("hyprox.routing.context");

    private final String pool;
    private final String backendId;
    private final String backendHost;
    private final int backendPort;
    private final String backendSource;
    private final DataPath dataPath;
    private final String reason;

    private RoutingDecisionContext(String pool,
                                   String backendId,
                                   String backendHost,
                                   int backendPort,
                                   String backendSource,
                                   DataPath dataPath,
                                   String reason) {
        this.pool = pool;
        this.backendId = backendId;
        this.backendHost = backendHost;
        this.backendPort = backendPort;
        this.backendSource = backendSource;
        this.dataPath = dataPath;
        this.reason = reason;
    }

    /**
     * Build a routing context snapshot from a decision.
     */
    public static RoutingDecisionContext from(RoutingDecision decision) {
        if (decision == null) {
            return new RoutingDecisionContext(null, null, null, 0, null, null, null);
        }
        BackendTarget backend = decision.backend();
        String backendId = backend == null ? null : backend.id();
        String backendHost = backend == null ? null : backend.host();
        int backendPort = backend == null ? 0 : backend.port();
        String backendSource = backend == null || backend.source() == null ? null : backend.source().name();
        return new RoutingDecisionContext(
                decision.pool(),
                backendId,
                backendHost,
                backendPort,
                backendSource,
                decision.dataPath(),
                decision.reason()
        );
    }
}
