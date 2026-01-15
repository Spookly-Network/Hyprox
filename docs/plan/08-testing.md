# Testing strategy

Unit
- Routing policy selection.
- Payload signing/verification.
- Migration state machine.
- Config parsing and validation.
- Packet framing encode/decode compatibility.

Integration
- QUIC handshake to proxy (local client stub).
- Redirect and referral paths.
- Full proxy forwarding with packet decode/encode.
- Authenticated flow (AuthGrant/AuthToken) and dev/password flow.
- Referral signature verification and replay protection.
- Migration handoff with controlled backend timing.

Load
- Simulate many concurrent sessions.
- Validate memory and buffer behavior during migration.
- Soak test with migration churn and packet tracing disabled/enabled.
