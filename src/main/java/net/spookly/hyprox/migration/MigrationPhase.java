package net.spookly.hyprox.migration;

/**
 * Phases for a seamless backend migration attempt.
 */
public enum MigrationPhase {
    /** No migration is currently running. */
    IDLE,
    /** Preparing backend B connection and initial handshake. */
    PREPARE,
    /** Authenticating the client session against backend B. */
    AUTH,
    /** Syncing setup or world state to backend B. */
    SYNC,
    /** Freezing client traffic and buffering packets. */
    FREEZE,
    /** Switching forwarding from backend A to backend B. */
    CUTOVER,
    /** Resuming buffered traffic and live forwarding to backend B. */
    RESUME,
    /** Cleaning up backend A and finalizing the handoff. */
    CLEANUP,
    /** Migration failed and rollback is required. */
    FAILED
}
