# Observability

Metrics
- Active connections: `hyprox_client_sessions`, `hyprox_backend_sessions`.
- Pool occupancy: `hyprox_pool_players{pool}`, `hyprox_pool_capacity{pool}`.
- Routing decisions: `hyprox_routes_total{pool,reason}`.
- Migration: `hyprox_migration_total{result}`, `hyprox_migration_duration_ms`.
- Errors: `hyprox_disconnects_total{reason}`, `hyprox_auth_failures_total`.
- Registry: `hyprox_registry_events_total{action,result}`.

Logging
- Connection lifecycle with correlation id (connect, auth, setup, close).
- Routing decisions with pool/backend id and reason.
- Migration state transitions with durations and failure reasons.
- Auth and referral verification failures (without leaking token contents).
- Redact or omit token fields, referral payloads, and identity tokens in all logs.
- Registry add/remove/drain events with backend id and source (orchestrator/agent).

Packet tracing
- Optional ring-buffer packet dump per session for debugging.
- Disabled by default, protected by config flag.
- Support sampling and size caps to avoid PII leakage and disk growth.
- Only allowlisted packet ids may be traced; sensitive fields are scrubbed.
- Trace data is memory-only unless a file path is explicitly configured.
