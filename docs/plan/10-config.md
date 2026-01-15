# Config schema (draft)

Format
- YAML (human-editable), with optional env expansion via `env:VAR_NAME`.

Top-level keys
- `proxy`: listener + QUIC + general mode.
- `auth`: pass-through vs terminate, referral signing.
- `routing`: static pools and rules.
- `migration`: settings for seamless handoff.
- `observability`: logging and metrics.

Schema (draft)
```
proxy:
  listen:
    host: string
    port: int
  mode: hybrid | redirect | full
  defaultPath: redirect | full
  fullProxyPools: [poolName]
  quic:
    alpn: [string]
    cert: path
    key: path
    mtu: int
  timeouts:
    handshakeMs: int
    idleMs: int

auth:
  mode: passthrough | terminate
  referral:
    signing:
      algorithm: hmac-sha256
      key: env:HYPROX_REFERRAL_HMAC | path
      ttlSeconds: int
      nonceBytes: int
    payloadMaxBytes: 4096

routing:
  defaultPool: poolName
  rules:
    - match:
        clientType: game | editor
        referralSource: poolName
      pool: poolName
  pools:
    poolName:
      policy: weighted | round_robin
      backends:
        - id: string
          host: string
          port: int
          weight: int
          maxPlayers: int
          tags: [string]
  health:
    intervalSeconds: int
    timeoutMs: int

migration:
  enabled: bool
  mode: full_proxy_only
  prepareTimeoutMs: int
  cutoverTimeoutMs: int
  bufferMaxPackets: int
  allowPools: [poolName]

observability:
  logging:
    level: trace | debug | info | warn | error
  metrics:
    prometheus:
      enabled: bool
      listen: string
```

Example config
```
proxy:
  listen:
    host: 0.0.0.0
    port: 20000
  mode: hybrid
  defaultPath: redirect
  fullProxyPools: ["game"]
  quic:
    alpn: ["hytale"]
    cert: config/certs/proxy.crt
    key: config/certs/proxy.key
    mtu: 1350
  timeouts:
    handshakeMs: 10000
    idleMs: 30000

auth:
  mode: passthrough
  referral:
    signing:
      algorithm: hmac-sha256
      key: env:HYPROX_REFERRAL_HMAC
      ttlSeconds: 30
      nonceBytes: 16
    payloadMaxBytes: 4096

routing:
  defaultPool: lobby
  rules:
    - match:
        clientType: game
      pool: lobby
    - match:
        referralSource: lobby
      pool: game
  pools:
    lobby:
      policy: weighted
      backends:
        - id: lobby-1
          host: 10.0.0.10
          port: 9000
          weight: 3
          maxPlayers: 150
          tags: ["lobby", "eu"]
        - id: lobby-2
          host: 10.0.0.11
          port: 9000
          weight: 2
          maxPlayers: 150
          tags: ["lobby", "eu"]
    game:
      policy: weighted
      backends:
        - id: game-1
          host: 10.0.1.10
          port: 9000
          weight: 1
          maxPlayers: 200
          tags: ["game", "eu"]
  health:
    intervalSeconds: 5
    timeoutMs: 500

migration:
  enabled: true
  mode: full_proxy_only
  prepareTimeoutMs: 3000
  cutoverTimeoutMs: 1500
  bufferMaxPackets: 256
  allowPools: ["game"]

observability:
  logging:
    level: info
  metrics:
    prometheus:
      enabled: true
      listen: 127.0.0.1:9100
```

Notes
- Keep `auth.mode: passthrough` for maximum security unless the full proxy must terminate auth to perform a feature.
- Referral payloads must be signed and validated by the target backend to prevent tampering.
