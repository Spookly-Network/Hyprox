# Security and auth

Principles
- Prefer end-to-end auth where possible.
- Only terminate or re-sign if required for routing or migration.
- Treat all auth tokens and identity data as sensitive; never log or persist them.

mTLS and trust anchors
- Proxy validates client certs against a pinned client CA bundle; no plaintext or unauthenticated fallback.
- Proxy validates backend server certs (CA + SAN/hostname or explicit allowlist).
- Backends pin the proxy CA and accept only proxy client certs for inbound QUIC.

Referral payloads
- Signed envelope with `keyId`, `issuedAt`, `ttlSeconds`, `nonce`, `targetBackendId`, and `sessionId` (bind to client uuid).
- Use HMAC-SHA256 or Ed25519; verify with constant-time compare.
- Use per-backend or per-pool keys; enforce key scope so a compromised backend cannot mint referrals for other pools.
- Enforce short TTL and nonce replay protection (LRU cache per key).
- If signature or target backend is invalid, ignore referral data and fall back to default routing.

Proxy auth strategy
- Default: pass-through, proxy does not terminate auth.
- Full proxy path: proxy only initiates backend auth using tokens received from the client.
- Seamless migration requires a backend-issued migration ticket (signed, short TTL). If unavailable, migration for that pool is disabled.
- If auth termination is enabled, store tokens in memory only and clear on disconnect.

Agent and control-plane security
- Agent -> proxy communication uses mTLS or shared HMAC token with nonce/timestamp.
- Agent endpoints are allowlisted by address and backend id.

Secrets management
- Secrets are loaded from env or files with restricted permissions.
- Support key rotation via multiple active keys (by `keyId`) with overlapping validity.
- Never emit secrets to logs or metrics.

Mitigations
- Validate redirect targets against configured pools and allowlists.
- Rate-limit handshake attempts per IP and cap concurrent sessions per IP.
- Apply global caps to migration buffers to avoid memory exhaustion.
