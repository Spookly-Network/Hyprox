package net.spookly.hyprox.registry;

/**
 * Listener for registry audit events.
 */
@FunctionalInterface
public interface RegistryEventListener {
    RegistryEventListener NOOP = event -> {
    };

    void onEvent(RegistryEvent event);
}
