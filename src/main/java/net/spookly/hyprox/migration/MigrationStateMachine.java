package net.spookly.hyprox.migration;

import java.time.Clock;
import java.time.Instant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.spookly.hyprox.config.ConfigException;
import net.spookly.hyprox.config.HyproxConfig;

/**
 * State machine for orchestrating seamless backend migration.
 */
public final class MigrationStateMachine {
    private static final String ERROR_DISABLED = "migration is disabled";
    private static final String ERROR_ALREADY_IN_PROGRESS = "migration already in progress";
    private static final String ERROR_NOT_IN_PROGRESS = "migration not in progress";
    private static final String ERROR_ALREADY_FAILED = "migration already failed";
    private static final String ERROR_CONTEXT_REQUIRED = "migration context is required";
    private static final String ERROR_CLIENT_UUID_REQUIRED = "migration client uuid is required";
    private static final String ERROR_SOURCE_REQUIRED = "migration source backend id is required";
    private static final String ERROR_TARGET_REQUIRED = "migration target backend id is required";
    private static final String ERROR_TICKET_MISSING = "migration ticket missing";
    private static final String ERROR_TICKET_INVALID = "migration ticket invalid";
    private static final String ERROR_CANNOT_RESET = "migration cannot reset from active state";
    private static final String ERROR_PREPARE_TIMEOUT = "migration prepare timeout";
    private static final String ERROR_CUTOVER_TIMEOUT = "migration cutover timeout";

    /**
     * Time source for deterministic timeouts.
     */
    private final Clock clock;
    /**
     * Optional metrics recorder for migration outcomes.
     */
    private final MigrationMetrics metrics;
    /**
     * Whether migration is enabled in config.
     */
    private final boolean enabled;
    /**
     * Whether a verified migration ticket is required to start.
     */
    private final boolean ticketRequired;
    /**
     * Timeout in milliseconds for the prepare/auth/sync stages.
     */
    private final long prepareTimeoutMs;
    /**
     * Timeout in milliseconds for the freeze/cutover/resume stages.
     */
    private final long cutoverTimeoutMs;

    /**
     * Current migration phase.
     */
    private MigrationPhase phase = MigrationPhase.IDLE;
    /**
     * Metadata for the active migration attempt.
     */
    private MigrationContext context;
    /**
     * Instant when the migration started.
     */
    private Instant startedAt;
    /**
     * Instant when the current phase began.
     */
    private Instant phaseStartedAt;
    /**
     * Deadline for the prepare/auth/sync stages.
     */
    private Instant prepareDeadline;
    /**
     * Deadline for the freeze/cutover/resume stages.
     */
    private Instant cutoverDeadline;
    /**
     * Failure reason when a migration ends in FAILED.
     */
    private String failureReason;

    public MigrationStateMachine(HyproxConfig config) {
        this(config, null, Clock.systemUTC());
    }

    public MigrationStateMachine(HyproxConfig config, Clock clock) {
        this(config, null, clock);
    }

    public MigrationStateMachine(HyproxConfig config, MigrationMetrics metrics, Clock clock) {
        if (config == null) {
            throw new ConfigException("Config is required for migration state machine");
        }
        this.metrics = metrics;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        HyproxConfig.MigrationConfig migration = config.migration;
        this.enabled = migration != null && Boolean.TRUE.equals(migration.enabled);
        this.ticketRequired = migration != null && Boolean.TRUE.equals(migration.ticketRequired);
        this.prepareTimeoutMs = normalizeTimeout(migration == null ? null : migration.prepareTimeoutMs);
        this.cutoverTimeoutMs = normalizeTimeout(migration == null ? null : migration.cutoverTimeoutMs);
    }

    public boolean enabled() {
        return enabled;
    }

    public MigrationPhase phase() {
        return phase;
    }

    public MigrationContext context() {
        return context;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant phaseStartedAt() {
        return phaseStartedAt;
    }

    public Instant prepareDeadline() {
        return prepareDeadline;
    }

    public Instant cutoverDeadline() {
        return cutoverDeadline;
    }

    public String failureReason() {
        return failureReason;
    }

    /**
     * Start a new migration attempt from backend A to backend B.
     */
    public TransitionResult start(MigrationContext context) {
        return start(context, null);
    }

    /**
     * Start a new migration attempt from backend A to backend B with a verified ticket.
     */
    public TransitionResult start(MigrationContext context, MigrationTicketService.VerifyResult ticketResult) {
        TransitionResult validation = validateStart(context);
        if (validation != null) {
            return validation;
        }
        String ticketError = ticketRequired ? validateTicket(ticketResult) : null;
        if (ticketError != null) {
            return TransitionResult.error(phase, ticketError);
        }
        return startInternal(context);
    }

    private TransitionResult startInternal(MigrationContext context) {
        Instant now = clock.instant();
        this.context = context;
        this.phase = MigrationPhase.PREPARE;
        this.startedAt = now;
        this.phaseStartedAt = now;
        this.prepareDeadline = deadlineFor(prepareTimeoutMs, now);
        this.cutoverDeadline = null;
        this.failureReason = null;
        return TransitionResult.ok(phase);
    }

    private TransitionResult validateStart(MigrationContext context) {
        if (!enabled) {
            return TransitionResult.error(phase, ERROR_DISABLED);
        }
        if (phase == MigrationPhase.FAILED) {
            return TransitionResult.error(phase, ERROR_ALREADY_FAILED);
        }
        if (phase != MigrationPhase.IDLE) {
            return TransitionResult.error(phase, ERROR_ALREADY_IN_PROGRESS);
        }
        if (context == null) {
            return TransitionResult.error(phase, ERROR_CONTEXT_REQUIRED);
        }
        if (context.clientUuid() == null) {
            return TransitionResult.error(phase, ERROR_CLIENT_UUID_REQUIRED);
        }
        if (isBlank(context.sourceBackendId())) {
            return TransitionResult.error(phase, ERROR_SOURCE_REQUIRED);
        }
        if (isBlank(context.targetBackendId())) {
            return TransitionResult.error(phase, ERROR_TARGET_REQUIRED);
        }
        return null;
    }

    /**
     * Record that backend B is prepared and ready for auth.
     */
    public TransitionResult markPrepared() {
        return advance(MigrationPhase.PREPARE, MigrationPhase.AUTH);
    }

    /**
     * Record that backend B authentication completed.
     */
    public TransitionResult markAuthComplete() {
        return advance(MigrationPhase.AUTH, MigrationPhase.SYNC);
    }

    /**
     * Record that backend B finished sync.
     */
    public TransitionResult markSyncComplete() {
        return advance(MigrationPhase.SYNC, MigrationPhase.FREEZE);
    }

    /**
     * Record that client traffic is frozen and cutover can begin.
     */
    public TransitionResult markFrozen() {
        return advance(MigrationPhase.FREEZE, MigrationPhase.CUTOVER);
    }

    /**
     * Record that cutover to backend B finished.
     */
    public TransitionResult markCutoverComplete() {
        return advance(MigrationPhase.CUTOVER, MigrationPhase.RESUME);
    }

    /**
     * Record that buffered traffic has been resumed to backend B.
     */
    public TransitionResult markResumed() {
        return advance(MigrationPhase.RESUME, MigrationPhase.CLEANUP);
    }

    /**
     * Record that backend A cleanup is complete.
     */
    public TransitionResult markCleanupComplete() {
        return advance(MigrationPhase.CLEANUP, MigrationPhase.IDLE);
    }

    /**
     * Fail the migration and capture a failure reason.
     */
    public TransitionResult fail(String reason) {
        TransitionResult timeout = checkTimeoutsInternal();
        if (timeout != null) {
            return timeout;
        }
        if (phase == MigrationPhase.IDLE) {
            return TransitionResult.error(phase, ERROR_NOT_IN_PROGRESS);
        }
        if (phase == MigrationPhase.FAILED) {
            return TransitionResult.error(phase, ERROR_ALREADY_FAILED);
        }
        Instant now = clock.instant();
        return failInternal(normalizeReason(reason), now);
    }

    /**
     * Reset after a failed migration to allow another attempt.
     */
    public TransitionResult reset() {
        if (phase != MigrationPhase.IDLE && phase != MigrationPhase.FAILED) {
            return TransitionResult.error(phase, ERROR_CANNOT_RESET);
        }
        clearState();
        return TransitionResult.ok(phase);
    }

    /**
     * Check configured timeouts and fail if exceeded.
     */
    public TransitionResult checkTimeouts() {
        TransitionResult timeout = checkTimeoutsInternal();
        return timeout == null ? TransitionResult.ok(phase) : timeout;
    }

    private TransitionResult advance(MigrationPhase expected, MigrationPhase next) {
        TransitionResult timeout = checkTimeoutsInternal();
        if (timeout != null) {
            return timeout;
        }
        if (phase != expected) {
            return TransitionResult.error(phase, "migration expected " + expected + " but was " + phase);
        }
        Instant now = clock.instant();
        if (next == MigrationPhase.IDLE) {
            recordSuccess(now);
            clearState();
            return TransitionResult.ok(phase);
        }
        phase = next;
        phaseStartedAt = now;
        if (next == MigrationPhase.FREEZE) {
            prepareDeadline = null;
            cutoverDeadline = deadlineFor(cutoverTimeoutMs, now);
        }
        return TransitionResult.ok(phase);
    }

    private TransitionResult checkTimeoutsInternal() {
        if (phase == MigrationPhase.IDLE || phase == MigrationPhase.FAILED) {
            return null;
        }
        Instant now = clock.instant();
        if (prepareDeadline != null && isPreparePhase(phase) && now.isAfter(prepareDeadline)) {
            return failInternal(ERROR_PREPARE_TIMEOUT, now);
        }
        if (cutoverDeadline != null && isCutoverPhase(phase) && now.isAfter(cutoverDeadline)) {
            return failInternal(ERROR_CUTOVER_TIMEOUT, now);
        }
        return null;
    }

    private TransitionResult failInternal(String reason, Instant now) {
        failureReason = reason;
        phase = MigrationPhase.FAILED;
        phaseStartedAt = now;
        prepareDeadline = null;
        cutoverDeadline = null;
        recordFailure(reason, now);
        return TransitionResult.error(phase, reason);
    }

    private void clearState() {
        phase = MigrationPhase.IDLE;
        context = null;
        startedAt = null;
        phaseStartedAt = null;
        prepareDeadline = null;
        cutoverDeadline = null;
        failureReason = null;
    }

    private Instant deadlineFor(long timeoutMs, Instant now) {
        if (timeoutMs <= 0) {
            return null;
        }
        return now.plusMillis(timeoutMs);
    }

    private void recordSuccess(Instant now) {
        if (metrics == null || startedAt == null || now == null) {
            return;
        }
        long durationMs = Math.max(0L, now.toEpochMilli() - startedAt.toEpochMilli());
        metrics.recordSuccess(durationMs);
    }

    private void recordFailure(String reason, Instant now) {
        if (metrics == null || startedAt == null || now == null) {
            return;
        }
        long durationMs = Math.max(0L, now.toEpochMilli() - startedAt.toEpochMilli());
        metrics.recordFailure(reason, durationMs);
    }

    private boolean isPreparePhase(MigrationPhase phase) {
        return phase == MigrationPhase.PREPARE
                || phase == MigrationPhase.AUTH
                || phase == MigrationPhase.SYNC;
    }

    private boolean isCutoverPhase(MigrationPhase phase) {
        return phase == MigrationPhase.FREEZE
                || phase == MigrationPhase.CUTOVER
                || phase == MigrationPhase.RESUME
                || phase == MigrationPhase.CLEANUP;
    }

    private long normalizeTimeout(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return 0L;
        }
        return timeoutMs;
    }

    private String normalizeReason(String reason) {
        if (isBlank(reason)) {
            return "migration failed";
        }
        return reason.trim();
    }

    private String validateTicket(MigrationTicketService.VerifyResult ticketResult) {
        if (ticketResult == null) {
            return ERROR_TICKET_MISSING;
        }
        if (!ticketResult.ok()) {
            if (isBlank(ticketResult.error())) {
                return ERROR_TICKET_MISSING;
            }
            return ticketResult.error();
        }
        if (ticketResult.ticket() == null) {
            return ERROR_TICKET_INVALID;
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public static final class TransitionResult {
        /**
         * Whether the transition succeeded without failure.
         */
        private final boolean ok;
        /**
         * Phase after applying the transition.
         */
        private final MigrationPhase phase;
        /**
         * Failure reason or validation error when ok is false.
         */
        private final String error;

        public static TransitionResult ok(MigrationPhase phase) {
            return new TransitionResult(true, phase, null);
        }

        public static TransitionResult error(MigrationPhase phase, String message) {
            return new TransitionResult(false, phase, message);
        }
    }
}
