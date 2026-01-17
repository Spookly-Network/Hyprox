# Usage and setup

This guide describes how to configure and run Hyprox, and explains the key config fields.

## Quick start

1) Create `config/hyprox.yaml` from the schema in `docs/plan/10-config.md`.
2) Validate the config:
```
./gradlew run --args="--config config/hyprox.yaml --dry-run"
```
3) Print the effective config (env expansion applied):
```
./gradlew run --args="--config config/hyprox.yaml --print-effective-config"
```
4) Build the jar:
```
./gradlew build
```
5) Run the proxy:
```
./gradlew run --args="--config config/hyprox.yaml"
```

Flags:
- `--config` or `-c`: path to config file.
- `--dry-run`: validate only.
- `--print-effective-config`: show the resolved config and exit.

## Run the compiled jar

The Gradle build produces a jar under `build/libs/`. Run it with:
```
java -jar build/libs/<jar-name>.jar --config config/hyprox.yaml
```

If you are unsure of the jar name, list the directory and pick the latest:
```
ls -lt build/libs
```

## Config overview

Top-level sections:
- `proxy`: QUIC listener settings and data path mode.
- `auth`: pass-through vs terminate and referral signing.
- `routing`: static pools and rules.
- `migration`: seamless handoff settings and ticket signing.
- `observability`: logging, tracing, metrics.
- `agent`: backend agent auth and allowlist.
- `registry`: dynamic backend registration.

### proxy

#### Data path modes

`proxy.mode` controls how traffic flows between clients and backends:

- `redirect`: Hyprox only handles the initial handshake and then sends a referral. The client connects directly
  to the backend. This is the lightest mode and is easiest to scale, but it requires clients to reach backends
  on the network.
- `full`: Hyprox stays in the data path and forwards packets between client and backend. This enables features
  like auth termination and migration orchestration, but it adds CPU cost and a small latency hop.
- `hybrid`: Combines both. Most pools use redirect, but specific pools can be forced to full proxy.

Choose based on networking and feature needs:
- Use `redirect` if clients can reach backends and you do not need auth termination or migration.
- Use `full` if backends should not be exposed to clients or if you need full-proxy features.
- Use `hybrid` when only some pools require full proxy (e.g., migration-enabled pools).

Related fields:
- `defaultPath`: `redirect` or `full` (used in `hybrid` when pool is not forced).
- `fullProxyPools`: pools that always use full proxy in `hybrid`.

#### Listener and QUIC settings

- `listen.host` / `listen.port`: bind address.
- `quic.alpn`: ALPN list for Hytale clients (usually `["hytale/1"]`).
- `quic.cert` / `quic.key`: proxy TLS cert/key.
- `quic.clientCa`: client CA for mTLS (required when `requireClientCert` is true).
- `quic.backendCa`: backend CA for full-proxy connections (required in `full` and `hybrid` when full proxy is used).
- `quic.backendSanAllowlist`: backend cert SAN allowlist (use to restrict which backend identities can connect).
- `quic.cipherSuites`: optional TLS 1.3 cipher suites (e.g. `TLS_AES_128_GCM_SHA256`).
- `quic.maxBidirectionalStreams` / `quic.maxUnidirectionalStreams`: stream caps.
- `quic.mtu`: UDP payload size cap.
- `timeouts.handshakeMs` / `timeouts.idleMs`: handshake and idle timeouts.
- `limits.handshakesPerMinutePerIp` / `limits.concurrentPerIp`: rate limits.

### auth

`auth.mode` controls whether Hyprox terminates auth or just forwards it:

- `passthrough`: Hyprox never reads or modifies auth tokens. This is the safest default.
- `terminate`: Hyprox consumes auth tokens to authenticate with backends. This is required only for specific
  features and increases risk if misconfigured.

Other auth settings:
- `referral.payloadMaxBytes`: max referral payload size.
- `referral.signing`: HMAC signing config.
  - `algorithm`: `hmac-sha256`.
  - `activeKeyId`: key to use for signing.
  - `keys`: key set with scope (`backend`, `pool`, `global`) and validity windows.
  - `ttlSeconds`: referral expiry window.
  - `nonceBytes`: minimum nonce size.

### routing

Routing picks a pool and backend for each client:

- `defaultPool`: pool used when no rule matches.
- `rules`: optional list of rule matches by `clientType` or `referralSource`.
- `pools`: static pool definitions.
  - `policy`: `weighted` or `round_robin`.
  - `backends`: list of backends with `id`, `host`, `port`, `weight`, `maxPlayers`, `tags`.
- `health`: optional active probe settings.
  - `intervalSeconds`: how often Hyprox runs a QUIC probe against each backend.
  - `timeoutMs`: maximum time to wait for a probe before marking it as failed.
  - Active probes update the same health score used by routing selection.

### migration

Migration orchestrates seamless handoff between backends. Use only when full proxy is enabled for the pool.

- `enabled`: toggle migration.
- `prepareTimeoutMs` / `cutoverTimeoutMs`: phase timeouts.
- `bufferMaxPackets` / `bufferGlobalMaxPackets`: buffer limits.
- `ticketRequired`: enforce verified tickets before starting.
- `ticketMaxAgeSeconds`: max ticket age accepted.
- `ticketSigning`: signing config, same shape as referral signing.
- `allowPools`: optional pool allowlist for migration.

### observability

- `logging.level`: log verbosity (`trace`/`debug`/`info`/`warn`/`error`).
- `logging.redactTokens`: redact auth tokens in logs.
- `tracing.enabled`: toggle packet tracing.
- `tracing.allowlistPacketIds`: trace only listed packet ids.
- `tracing.memoryOnly`: keep traces in memory only.
- `metrics.prometheus.enabled`: toggle Prometheus exporter.
- `metrics.prometheus.listen`: bind address for metrics endpoint.

### agent

- `auth.mode`: `mtls` or `hmac`.
- `auth.sharedKey`: HMAC key for agent auth.
- `auth.clientCa`: CA for agent mTLS.
- `allowlist`: backend id and optional source address allowlist.

### registry

The registry is an optional control plane for dynamic backend registration.

- `enabled`: toggle registry control plane.
- `listen`: registry bind address.
- `allowedNetworks`: list of CIDRs allowed to call registry endpoints.
- `allowedPorts`: allowed backend ports for registrations.
- `allowLoopback` / `allowPublicAddresses`: host validation toggles.
- `maxRequestBytes` / `maxListResults`: request size and list caps.
- `rateLimitPerMinute`: per-IP rate limit.
- `auth`: registry auth mode and parameters.
- `allowlist`: orchestrator allowlist with pool and backend id prefix scopes.
- `defaults`: TTL, heartbeat grace, and drain defaults.

For registry API usage and examples, see `docs/plan/13-registry-usage.md`.

## Suggested starting setups

### Simple redirect setup (easiest)

Use when clients can reach backends directly.

- `proxy.mode: redirect`
- `auth.mode: passthrough`
- Configure `routing.pools` with backend hosts reachable by clients.
- Provide `proxy.quic.clientCa` if clients use mTLS.

### Full proxy setup (secure backend network)

Use when backends should not be exposed to clients.

- `proxy.mode: full`
- `auth.mode: passthrough` (unless a full-proxy feature needs termination)
- Configure `proxy.quic.backendCa` and (optionally) `proxy.quic.backendSanAllowlist`.
- Ensure backends are reachable from the proxy.
