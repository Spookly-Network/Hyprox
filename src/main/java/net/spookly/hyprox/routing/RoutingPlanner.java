package net.spookly.hyprox.routing;

import java.util.Objects;

/**
 * Combines routing selection with transport path choice.
 */
public final class RoutingPlanner {
    private final RoutingService routingService;
    private final PathSelector pathSelector;

    public RoutingPlanner(RoutingService routingService, PathSelector pathSelector) {
        this.routingService = Objects.requireNonNull(routingService, "routingService");
        this.pathSelector = Objects.requireNonNull(pathSelector, "pathSelector");
    }

    /**
     * Decide target backend and data path for a request.
     */
    public RoutingDecision decide(RoutingRequest request) {
        RoutingResult result = routingService.route(request);
        DataPath path = pathSelector.select(result.pool());
        return new RoutingDecision(result.pool(), result.backend(), result.reservation(), path, result.reason());
    }
}
