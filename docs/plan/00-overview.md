# Hyprox multi-server proxy plan

Goals
- Hybrid proxy: prefer redirect/referral but support full QUIC data-path when required.
- Static pools: explicit backend sets with weights and hard limits.
- Dynamic registration: allow orchestrator or agent to add/remove backends at runtime.
- Seamless migration: in-session moves without client menu or reconnect.
- Secure auth: terminate at proxy only if required; otherwise pass-through with signed handoff payloads.

Non-goals (initial)
- Dynamic matchmaking, auto-scaling, or region inference.
- TLS/PKI management UI.
- Protocol fuzzing or anti-cheat.

Key decisions
- Entry point is a standalone proxy process; backend plugin is optional but recommended.
- Default path uses Hytale redirect/referral features where possible; full proxy is used only for features that require packet inspection or hot migration.

Definitions
- Redirect path: proxy accepts initial handshake and then instructs client to connect to a backend.
- Referral path: backend server tells client to connect to another backend, with a signed payload.
- Full proxy path: client connects to proxy; proxy opens backend connection and forwards packets.
