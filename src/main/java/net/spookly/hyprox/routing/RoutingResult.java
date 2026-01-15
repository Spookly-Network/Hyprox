package net.spookly.hyprox.routing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Routing decision outcome for a request.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class RoutingResult {
    private final String pool;
    private final BackendTarget backend;
    private final String reason;
}
