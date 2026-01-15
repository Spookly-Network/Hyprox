# Architecture

Components
1) hyprox-proxy (standalone)
- Listens for QUIC connections from clients.
- Decides routing and migration.
- Supports two data paths: redirect/referral and full proxy.

2) hyprox-agent (optional backend plugin)
- Signs referral payloads (HMAC or signature).
- Publishes health/metadata to proxy (player count, shard, map).
- Triggers referrals for in-game transfers.

3) control-plane (file-based config, optional service later)
- Static pool definitions, weights, capacity.
- Secrets for signing referral payloads.

Data paths
- Redirect path: client -> proxy (handshake) -> backend; proxy exits data path.
- Full proxy path: client <-> proxy <-> backend; proxy remains in data path.
- Migration path: client stays connected to proxy; proxy creates backend B connection, switches forwarding when safe.

Trust boundaries
- Proxy is trusted to enforce routing and authenticate payloads.
- Backend plugin is trusted only for its own server context and shared signing secret.

Failure modes
- Backend down: proxy redirects to fallback pool.
- Proxy down: clients reconnect to another proxy if configured; otherwise fail.
- Migration failure: keep old backend until swap is confirmed or fallback to referral.
