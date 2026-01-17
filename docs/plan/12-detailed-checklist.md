# Detailed implementation checklist

## Foundations
- [x] Confirm config schema matches implementation and add missing fields to `HyproxConfig`.
- [x] Add config examples for `redirect`, `full`, and `hybrid` modes.
- [x] Validate file permissions for key/cert inputs (warn if world-readable).
- [x] Add CLI flags: `--config`, `--dry-run`, `--print-effective-config`.

## QUIC listener and handshake
- [x] Complete QUIC server settings (idle timeout, stream limits, MTU).
- [x] Map Hytale client certificate to per-session context.
- [x] Add per-IP rate limiting and concurrent session caps in the listener.
- [x] Implement handshake timeout cleanup for half-open streams.

## Routing and pools
- [x] Add pool capacity tracking and reject when `maxPlayers` exceeded.
- [x] Add pool health scoring (passive failures + optional active ping).
- [x] Add consistent backend selection when weights change.
- [x] Persist routing decision reason for metrics/logging.

## Referral payloads
- [x] Define referral envelope schema (keyId, issuedAt, ttl, nonce, targetBackendId, clientUuid).
- [x] Implement HMAC signing for outgoing referrals.
- [x] Implement verification, TTL, nonce replay cache, and scope checks.
- [x] Only trust referral host when signature and target backend are valid.
- [x] Add config for key rotation and overlapping validity.

## Dynamic registry
- [x] Enforce backend cert SAN allowlist on registration activation.
- [x] Add explicit port allowlist and reject loopback/public addresses.
- [x] Add request size limits and JSON parsing guards.
- [x] Add list endpoint filters and pagination if needed.
- [x] Emit registry audit events (add, heartbeat, drain, expire).

## Full proxy data path
- [x] Add backend QUIC client connector with mTLS and CA pinning.
- [x] Implement backend stream pipeline mirroring Hytale packet handlers.
- [x] Add packet forwarding (client <-> backend) with backpressure control.
- [x] Handle disconnect propagation and graceful close ordering.
- [x] Add data-path metrics (bytes, packets, latency).

## Auth handling
- [x] Implement passthrough auth flow (no token modification).
- [x] Implement terminate mode with in-memory token storage only.
- [x] Add token redaction in logs and trace output.
- [x] Validate protocol hash and reject mismatches early.

## Seamless migration
- [x] Define migration ticket schema and signing rules.
- [x] Implement prepare/auth/sync/freeze/cutover/resume/cleanup state machine.
- [x] Add bounded buffers with global caps and rollback logic.
- [x] Add migration metrics and failure reason tracking.
- [ ] Add safety: disable migration if ticket invalid or missing.

## Backend agent (optional)
- [ ] Implement referral signer plugin in server context.
- [ ] Add health/metadata publishing with mTLS or HMAC auth.
- [ ] Enforce per-backend scope for signed referrals.

## Observability
- [ ] Add structured logging with correlation IDs.
- [ ] Implement metrics registry and Prometheus endpoint.
- [ ] Add optional packet tracing with allowlist and redaction.
- [ ] Add audit logs for registry and routing decisions.

## Security hardening
- [ ] Add TLS/QUIC cipher suite configuration and defaults.
- [ ] Enforce nonce replay protection for registry and referral payloads.
- [x] Add request rate limits on registry endpoints.
- [x] Add allowlist checks for registry source IPs and orchestrator IDs.

## Testing
- [x] Unit tests for routing, selection, referral signing/verification.
- [ ] Unit tests for registry validation (allowlists, TTL, drain).
- [ ] Integration tests for redirect/referral paths.
- [ ] Integration tests for full-proxy forwarding (mock backend).
- [ ] Integration tests for migration state machine and rollback.
- [ ] Load tests for concurrent sessions and buffer limits.

## Docs and ops
- [ ] Document runbooks for cert rotation and key management.
- [ ] Document registry usage for orchestrators (examples + curl).
- [ ] Add troubleshooting guide for common disconnects and TLS errors.
- [ ] Add release checklist and versioning notes.
