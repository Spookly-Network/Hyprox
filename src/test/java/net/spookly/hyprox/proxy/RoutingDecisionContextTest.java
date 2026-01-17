package net.spookly.hyprox.proxy;

import net.spookly.hyprox.routing.BackendSource;
import net.spookly.hyprox.routing.BackendTarget;
import net.spookly.hyprox.routing.DataPath;
import net.spookly.hyprox.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoutingDecisionContextTest {
    @Test
    void capturesDecisionFields() {
        BackendTarget backend = new BackendTarget(
                "lobby-1",
                "lobby",
                "10.0.0.10",
                9000,
                1,
                150,
                List.of("lobby"),
                BackendSource.STATIC,
                false
        );
        RoutingDecision decision = new RoutingDecision("lobby", backend, null, DataPath.REDIRECT, "selected");

        RoutingDecisionContext context = RoutingDecisionContext.from(decision);

        assertEquals("lobby", context.pool());
        assertEquals("lobby-1", context.backendId());
        assertEquals("10.0.0.10", context.backendHost());
        assertEquals(9000, context.backendPort());
        assertEquals("STATIC", context.backendSource());
        assertEquals(DataPath.REDIRECT, context.dataPath());
        assertEquals("selected", context.reason());
    }

    @Test
    void handlesNullDecision() {
        RoutingDecisionContext context = RoutingDecisionContext.from(null);

        assertNull(context.pool());
        assertNull(context.backendId());
        assertNull(context.backendHost());
        assertEquals(0, context.backendPort());
        assertNull(context.backendSource());
        assertNull(context.dataPath());
        assertNull(context.reason());
    }
}
