package net.spookly.hyprox.routing;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Immutable routing target for a backend server.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class BackendTarget {
    private final String id;
    private final String pool;
    private final String host;
    private final int port;
    private final int weight;
    private final Integer maxPlayers;
    private final List<String> tags;
    private final BackendSource source;
    private final boolean draining;
}
