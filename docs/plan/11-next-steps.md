# Next steps

- Implement referral payload signing/verification and validate referral data before routing.
- Add full-proxy data path: backend QUIC connect, packet forwarding, and teardown.
- Add basic proxy -> backend auth handling for passthrough and terminate modes.
- Implement migration ticket flow and cutover state machine for seamless moves.
- Wire optional active health probes into the routing health tracker.
- Add observability: metrics counters, structured logs, and optional packet tracing.
- Add integration tests for redirect, referral, registry-driven routing, and full-proxy forwarding.
- Add load tests for concurrent sessions and migration buffer limits.
- Document runbooks for cert rotation, key management, and registry auth.
