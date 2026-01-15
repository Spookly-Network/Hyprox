# Testing strategy

Unit
- Routing policy selection.
- Payload signing/verification.
- Migration state machine.
- Config parsing and validation.
- Packet framing encode/decode compatibility.
- Key scope enforcement and key rotation selection.
- Registry entry validation (TTL, drain, allowlists).

Integration
- QUIC handshake to proxy (local client stub).
- Redirect and referral paths.
- Full proxy forwarding with packet decode/encode.
- Authenticated flow (AuthGrant/AuthToken) and dev/password flow.
- Referral signature verification and replay protection.
- mTLS validation failures (unknown client CA, backend cert mismatch).
- Agent auth (mTLS/HMAC) and allowlist enforcement.
- Migration handoff with controlled backend timing.
- Dynamic backend registration, heartbeat renewal, and automatic removal.
- Registry HMAC replay protection and orchestrator pool scope enforcement.
- Registry port allowlist enforcement and duplicate backend id rejection.

Load
- Simulate many concurrent sessions.
- Validate memory and buffer behavior during migration.
- Soak test with migration churn and packet tracing disabled/enabled.
