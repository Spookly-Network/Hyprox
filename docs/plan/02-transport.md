# QUIC transport

Stack
- Netty QUIC for both listener (server) and connector (client) endpoints.
- Separate event loops for client-side and backend-side connections to reduce head-of-line risk.

Sessions
- ClientSession: QUIC connection from player to proxy.
- BackendSession: QUIC connection from proxy to a backend server.
- SessionPair: mapping of ClientSession <-> BackendSession.

Framing and packet IO
- Packet frames are length-prefixed: 4 bytes length (LE) + 4 bytes packet id (LE), followed by payload.
- Packet ids and max sizes are defined in `PacketRegistry` and enforced by `PacketDecoder`.
- Use the same PacketIO encoder/decoder behavior as the server pipeline to avoid protocol drift.

QUIC security and ALPN
- ALPN observed in server transport: `hytale/1`.
- Server requires mTLS for QUIC (client cert must be present).
- Proxy terminates QUIC from clients and validates client certs against a pinned client CA bundle.
- Proxy presents its own client cert when connecting to backends; backends pin the proxy CA and optionally enforce SAN allowlists.
- Backend server identity is verified by the proxy (CA + SAN/hostname) before any packets are sent.

Transport requirements
- Support ALPN/handshake as required by Hytale client.
- Enforce backpressure and bounded queues to avoid buffer growth during migration.
- Match server QUIC defaults where possible (idle timeouts, stream limits).

Hybrid handling
- Redirect path: parse `Connect` and minimal auth; respond with `ClientReferral` when routing is decided.
- Full proxy path: forward packets after decode/encode, maintaining stream/packet ordering per stream.

Netty pipeline (proxy data path)
- QUIC stream pipeline mirrors `HytaleChannelInitializer`:
  - `PacketDecoder` -> optional `RateLimitHandler` -> `PacketEncoder` -> `PacketArrayEncoder` -> `ProxyBridgeHandler`.

Connection lifecycle
- On QUIC connection: create ClientSession, attach correlation id, record client cert fingerprint for audit only.
- On first stream: install packet pipeline and Initial handler (Connect).
- On backend connect: establish BackendSession with same protocol hash and client auth context.

Timeouts and rate limiting
- Initial timeout for handshake (mirrors server initial timeout).
- Auth timeout for token exchange.
- Idle timeout for established sessions.
- Rate limits for packet burst and packets/sec.
