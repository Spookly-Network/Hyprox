package net.spookly.hyprox.registry;

/**
 * Default registry audit logger that emits one line per event.
 */
public final class RegistryAuditLogger implements RegistryEventListener {
    public static final RegistryAuditLogger INSTANCE = new RegistryAuditLogger();

    private RegistryAuditLogger() {
    }

    @Override
    public void onEvent(RegistryEvent event) {
        StringBuilder builder = new StringBuilder("registry_event");
        append(builder, "type", event.type());
        append(builder, "backendId", event.backendId());
        append(builder, "pool", event.pool());
        append(builder, "host", event.host());
        append(builder, "port", event.port());
        append(builder, "weight", event.weight());
        append(builder, "maxPlayers", event.maxPlayers());
        append(builder, "orchestratorId", event.orchestratorId());
        append(builder, "lastHeartbeat", event.lastHeartbeat());
        append(builder, "expiresAt", event.expiresAt());
        append(builder, "draining", event.draining());
        append(builder, "timestamp", event.timestamp());
        System.out.println(builder);
    }

    private void append(StringBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        builder.append(' ').append(key).append('=').append(value);
    }
}
