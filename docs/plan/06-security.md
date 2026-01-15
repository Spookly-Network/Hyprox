# Security and auth

Principles
- Prefer end-to-end auth where possible.
- Only terminate or re-sign if required for routing or migration.

Referral payloads
- Always sign with HMAC (shared secret) or asymmetric signature.
- Include nonce, timestamp, target backend id, and session id.
- Enforce short TTL and replay protection on backend.

Proxy auth strategy
- Default: pass-through, proxy does not terminate auth.
- Full proxy path: proxy may need to terminate and re-initiate backend auth if protocol requires.

Secrets management
- File-based secrets in config (initial), later support env or vault.

Mitigations
- Validate redirect targets against configured pools.
- Rate-limit handshake attempts per IP.
