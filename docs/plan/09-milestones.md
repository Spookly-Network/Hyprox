# Milestones

M1: Redirector proxy
- QUIC listener and minimal handshake parsing.
- Static pools and redirect decision.
- Config load + validation.
- Registry control API for dynamic backend add/remove.

M2: Backend plugin (optional)
- Referral payload signer.
- Health/metadata reporting to proxy.

M3: Full proxy data path
- Packet decode/encode pipeline.
- Forwarding for baseline gameplay.

M4: Seamless migration
- Dual-backend session handoff.
- Migration state machine + timeouts.

M5: Hardening
- Observability, rate limits, packet tracing toggles.
- Load testing and tuning.
