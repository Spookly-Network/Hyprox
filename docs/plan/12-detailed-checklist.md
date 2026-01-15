# Detailed implementation checklist

## Foundations
- [ ] Confirm config schema matches implementation and add missing fields to `HyproxConfig`.
- [ ] Add config examples for `redirect`, `full`, and `hybrid` modes.
- [ ] Validate file permissions for key/cert inputs (warn if world-readable).
- [ ] Add CLI flags: `--config`, `--dry-run`, `--print-effective-config`.

## QUIC listener and handshake
- [ ] Complete QUIC server settings (idle timeout, stream limits, MTU).
- [ ] Map Hytale client certificate to per-session context.
- [ ] Add per-IP rate limiting and concurrent session caps in the listener.
- [ ] Implement handshake timeout cleanup for half-open streams.

## Routing and pools
- [ ] Add pool capacity tracking and reject when `maxPlayers` exceeded.
- [ ] Add pool health scoring (passive failures + optional active ping).
- [ ] Add consistent backend selection when weights change.
- [ ] Persist routing decision reason for metrics/logging.

## Referral payloads
- [ ] Define referral envelope schema (keyId, issuedAt, ttl, nonce, targetBackendId, clientUuid).
- [ ] Implement HMAC signing for outgoing referrals.
- [ ] Implement verification, TTL, nonce replay cache, and scope checks.
- [ ] Only trust referral host when signature and target backend are valid.
- [ ] Add config for key rotation and overlapping validity.

## Dynamic registry
- [ ] Enforce backend cert SAN allowlist on registration activation.
- [ ] Add explicit port allowlist and reject loopback/public addresses.
- [ ] Add request size limits and JSON parsing guards.
- [ ] Add list endpoint filters and pagination if needed.
- [ ] Emit registry audit events (add, heartbeat, drain, expire).

## Full proxy data path
- [ ] Add backend QUIC client connector with mTLS and CA pinning.
- [ ] Implement backend stream pipeline mirroring Hytale packet handlers.
- [ ] Add packet forwarding (client <-> backend) with backpressure control.
- [ ] Handle disconnect propagation and graceful close ordering.
- [ ] Add data-path metrics (bytes, packets, latency).

## Auth handling
- [ ] Implement passthrough auth flow (no token modification).
- [ ] Implement terminate mode with in-memory token storage only.
- [ ] Add token redaction in logs and trace output.
- [ ] Validate protocol hash and reject mismatches early.

## Seamless migration
- [ ] Define migration ticket schema and signing rules.
- [ ] Implement prepare/auth/sync/freeze/cutover/resume/cleanup state machine.
- [ ] Add bounded buffers with global caps and rollback logic.
- [ ] Add migration metrics and failure reason tracking.
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
- [ ] Add request rate limits on registry endpoints.
- [ ] Add allowlist checks for registry source IPs and orchestrator IDs.

## Testing
- [ ] Unit tests for routing, selection, referral signing/verification.
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
