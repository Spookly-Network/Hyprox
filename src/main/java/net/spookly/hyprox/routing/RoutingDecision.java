package net.spookly.hyprox.routing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Combined routing and path decision for a client session.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class RoutingDecision {
    private final String pool;
    private final BackendTarget backend;
    private final BackendReservation reservation;
    private final DataPath dataPath;
    private final String reason;
}
