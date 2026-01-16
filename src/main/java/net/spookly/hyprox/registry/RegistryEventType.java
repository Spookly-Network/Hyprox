package net.spookly.hyprox.registry;

/**
 * Audit event types emitted by the registry store.
 */
public enum RegistryEventType {
    REGISTER,
    HEARTBEAT,
    DRAIN,
    EXPIRE
}
