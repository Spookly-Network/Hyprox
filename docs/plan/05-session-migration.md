# Seamless migration

Goal
Move a connected player between backends without client reconnect or menu return.

Requirements
- Full proxy data path only (redirect/referral cannot be seamless).
- Proxy must be allowed to authenticate to backends on behalf of the client.
- Backend cooperation is strongly recommended for state transfer and consistency.

State machine (full proxy)
1) Idle (A): client <-> backend A forwarded normally.
2) Prepare: select backend B, open QUIC connection with mTLS, send `Connect`.
3) Auth: complete auth with B (reuse client tokens only if permitted).
4) Sync: send or await setup state on B (world settings, server info).
5) Freeze: stop client->A forwarding; buffer client->proxy packets.
6) Cutover: switch forwarding to B when it is ready.
7) Resume: drain buffered packets to B, resume live forwarding.
8) Cleanup: close A after a safe window.
9) Failed: revert to A or fallback to referral.

Session data to capture (initial list)
- From `Connect`: `protocolHash`, `clientType`, `language`, `uuid`, `username`, `identityToken`, `referralData`.
- From auth flow: `accessToken`, `serverAuthorizationGrant`, `serverAccessToken`.
- From setup: `SetClientId`, `ServerInfo`, `WorldSettings`, `ViewRadius`, `PlayerOptions`.
- From world entry: `JoinWorld` (world UUID and flags).

Handoff sequencing (full proxy)
- Open B connection and complete auth before freezing client traffic.
- Freeze client->A, flush A->client to avoid diverging state.
- Switch to B only after B is ready to accept gameplay packets.
- Keep a short overlap window where A is still connected for rollback.

Buffering
- Queue outbound client packets while in Freeze/Prepare (bounded by size or packet count).
- If buffer exceeds limit or timeout is hit, abort and roll back.

Fallbacks
- If B setup/auth fails: resume A and clear buffers.
- If cutover fails after freeze: attempt referral fallback or disconnect with reason.

Risks
- Reusing client tokens may not be allowed; validate server expectations.
- Missing setup or world state packets can cause desync.
- Packet ordering across streams must be preserved during cutover.

Deliverables
- Verified minimal packet set required for hot handoff (via trace).
- Migration state machine implementation + timeouts and rollback rules.
