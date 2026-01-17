package net.spookly.hyprox.migration;

import java.util.UUID;

/**
 * Immutable metadata for a single migration attempt.
 */
public final class MigrationContext {
    /**
     * Client UUID bound to the migration.
     */
    private final UUID clientUuid;
    /**
     * Backend id the client is migrating from.
     */
    private final String sourceBackendId;
    /**
     * Backend id the client is migrating to.
     */
    private final String targetBackendId;

    public MigrationContext(UUID clientUuid, String sourceBackendId, String targetBackendId) {
        this.clientUuid = clientUuid;
        this.sourceBackendId = sourceBackendId;
        this.targetBackendId = targetBackendId;
    }

    public UUID clientUuid() {
        return clientUuid;
    }

    public String sourceBackendId() {
        return sourceBackendId;
    }

    public String targetBackendId() {
        return targetBackendId;
    }
}
