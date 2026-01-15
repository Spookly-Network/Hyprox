package net.spookly.hyprox.routing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Routing context derived from the client handshake.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class RoutingRequest {
    private final String clientType;
    private final String referralSource;
}
