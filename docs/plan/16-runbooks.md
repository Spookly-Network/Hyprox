# Runbooks

This document focuses on operational steps for common maintenance tasks.

## Certificate rotation (proxy QUIC listener)

Goal: rotate `proxy.quic.cert` + `proxy.quic.key` without downtime.

1) Generate the new certificate and key for the proxy listener.
2) Update `proxy.quic.cert` and `proxy.quic.key` paths in the config.
3) If clients validate the proxy via a dedicated CA, update the client-side trust store first.
4) Use `--dry-run` to validate config syntax before deploying.
5) Roll the proxy nodes one at a time to avoid downtime.
6) Confirm clients can connect and no handshake errors appear in logs.

Notes:
- Use a combined CA bundle file when you need overlap between old and new issuer CAs.
- Keep key files non-world-readable; Hyprox prints a warning if permissions are too open.

## Backend CA rotation (proxy -> backend mTLS)

Goal: rotate the backend CA used by Hyprox to validate backend certificates.

1) Issue backend certificates signed by the new CA.
2) Create a combined CA bundle containing the old and new CA certificates.
3) Set `proxy.quic.backendCa` to the combined bundle.
4) Roll the proxy nodes, then roll the backend nodes to the new certs.
5) After all backends are rotated, update `proxy.quic.backendCa` to only the new CA.

## Client CA rotation (proxy requiring client certs)

Goal: rotate the client CA used to validate incoming clients.

1) Distribute new client certificates to clients.
2) Create a combined CA bundle (old + new).
3) Update `proxy.quic.clientCa` to the combined bundle.
4) Roll the proxy nodes.
5) After all clients have rotated, update the bundle to only the new CA.

## HMAC key rotation (referrals, migration, registry, agent)

Goal: rotate shared HMAC keys without rejecting existing tokens.

1) Add a new key entry to the relevant `keys` list.
2) Set `activeKeyId` to the new key.
3) Keep the old key entry until the longest TTL window has expired.
4) Roll all proxy nodes.
5) Remove the old key entry after the overlap window.

Notes:
- Referral and migration signing support multiple keys for overlap.
- Registry/agent HMAC auth uses a single `sharedKey`; rotate by updating all parties and then
  rolling the proxy last to avoid mismatches.

## Registry auth rotation

### HMAC mode
1) Generate a new shared key.
2) Update orchestrators to use the new key first.
3) Update `registry.auth.sharedKey` in the proxy config.
4) Roll the proxy nodes.

### mTLS mode
1) Issue new client certificates for orchestrators.
2) Update `registry.auth.clientCa` with a combined CA bundle.
3) Roll the proxy nodes.
4) Rotate orchestrator certs.
5) Remove the old CA from the bundle after all orchestrators have rotated.
