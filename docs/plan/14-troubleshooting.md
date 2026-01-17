# Troubleshooting guide

This guide focuses on common disconnects and TLS/QUIC issues seen in Hyprox.

## Quick checks

- Run with `--dry-run` to validate config without binding sockets.
- Run with `--print-effective-config` to confirm env expansion and defaults.
- Review startup warnings about world-readable key/cert files.
- Confirm `proxy.mode`, `proxy.quic.alpn`, and backend pool definitions match the intended path.

## Client disconnect reasons

Hyprox sends a `Disconnect` reason string to the client. Common reasons and fixes:

- `unexpected packet`: client did not send `Connect` first; check client/proxy protocol alignment.
- `missing protocol hash`: client sent an empty protocol hash; verify client build.
- `protocol hash too long`: client sent an oversized hash; verify client build.
- `protocol hash mismatch`: client and proxy expect different protocol versions.
- `handshake timeout`: no `Connect` before `proxy.timeouts.handshakeMs`; check client reachability.
- `rate limited`: exceeded `proxy.limits.handshakesPerMinutePerIp`.
- `too many sessions`: exceeded `proxy.limits.concurrentPerIp`.
- `invalid backend port`: backend port is missing or out of range (static or registry data).
- `referral signing failed`: check `auth.referral.signing` keys, `activeKeyId`, and validity windows.
- `backend connection failed`: QUIC to backend failed; check backend reachability and CA/SAN settings.
- `no backend available` or `routing failed: <reason>`: see routing reasons below.

Routing reasons:
- `no_pool`: routing rules did not match and `routing.defaultPool` is missing.
- `no_backends`: selected pool has no configured backends.
- `pool_full`: all candidates are at capacity.

## Backend QUIC/TLS failures

When backend connections fail:
- Verify `proxy.quic.backendCa` points to the backend CA bundle.
- Ensure backend certificate SANs match the backend host or `proxy.quic.backendSanAllowlist`.
- Confirm backend ports match `registry.allowedPorts` and static config.
- Check network ACLs and firewall rules between proxy and backend.

## Cipher suite configuration

If `proxy.quic.cipherSuites` is set and the QUIC runtime does not support configuring ciphers,
Hyprox fails fast with: `proxy.quic.cipherSuites is not supported by this QUIC runtime`.
Remove the field or upgrade the QUIC runtime to one that supports cipher configuration.

## Registry API errors (HMAC mode)

Common HTTP responses:
- `401`: missing headers, invalid signature, nonce too short, replayed nonce, or clock skew.
- `403`: source IP not in `registry.allowedNetworks`.
- `400`: invalid JSON or allowlist validation failures (pool, backend id prefix, port).
- `413`: request exceeds `registry.maxRequestBytes`.
- `429`: exceeded `registry.rateLimitPerMinute`.

## Referral and migration validation

- Referral payloads failing verification are ignored and routing falls back to default selection.
- Migration start fails when the ticket is missing or invalid; verify
  `migration.ticketSigning` keys, TTLs, and `migration.ticketRequired`.
