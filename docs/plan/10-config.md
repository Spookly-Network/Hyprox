# Config schema (draft)

Format
- YAML (human-editable), with optional env expansion via `env:VAR_NAME`.

Top-level keys
- `proxy`: listener + QUIC + general mode.
- `auth`: pass-through vs terminate, referral signing.
- `routing`: static pools and rules.
- `migration`: settings for seamless handoff.
- `observability`: logging and metrics.
- `agent`: backend agent auth and allowlist.
- `registry`: dynamic backend registration settings.

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
    cipherSuites: [string]
    cert: path
    key: path
    clientCa: path
    backendCa: path
    requireClientCert: bool
    backendSanAllowlist: [string]
    maxBidirectionalStreams: int
    maxUnidirectionalStreams: int
    mtu: int
  timeouts:
    handshakeMs: int
    idleMs: int
  limits:
    handshakesPerMinutePerIp: int
    concurrentPerIp: int

auth:
  mode: passthrough | terminate
  referral:
    signing:
      algorithm: hmac-sha256
      activeKeyId: string
      keys:
        - keyId: string
          key: env:HYPROX_REFERRAL_HMAC | path
          scope: backend | pool | global
          scopeId: string
          validFrom: iso8601
          validTo: iso8601
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
  bufferGlobalMaxPackets: int
  ticketRequired: bool
  ticketMaxAgeSeconds: int
  ticketSigning:
    algorithm: hmac-sha256
    activeKeyId: string
    ttlSeconds: int
    nonceBytes: int
    keys:
      - keyId: string
        key: env:HYPROX_MIGRATION_HMAC | string
        scope: backend | pool | global
        scopeId: string
        validFrom: timestamp
        validTo: timestamp
  allowPools: [poolName]

observability:
  logging:
    level: trace | debug | info | warn | error
    redactTokens: bool
  tracing:
    enabled: bool
    allowlistPacketIds: [int]
    memoryOnly: bool
  metrics:
    prometheus:
      enabled: bool
      listen: string

agent:
  auth:
    mode: mtls | hmac
    sharedKey: env:HYPROX_AGENT_HMAC | path
    clientCa: path
  allowlist:
    - backendId: string
      address: string

registry:
  enabled: bool
  listen: string
  maxRequestBytes: int
  maxListResults: int
  rateLimitPerMinute: int
  allowedPorts: [int]
  allowLoopback: bool
  allowPublicAddresses: bool
  auth:
    mode: mtls | hmac
    clientCa: path
    sharedKey: env:HYPROX_REGISTRY_HMAC | path
    nonceBytes: int
    clockSkewSeconds: int
  allowlist:
    - orchestratorId: string
      address: string
      allowedPools: [poolName]
      allowedBackendIdPrefixes: [string]
  defaults:
    ttlSeconds: int
    heartbeatGraceSeconds: int
    drainTimeoutSeconds: int
  allowedNetworks: [cidr]
```

Example config (hybrid)
```
proxy:
  listen:
    host: 0.0.0.0
    port: 20000
  mode: hybrid
  defaultPath: redirect
  fullProxyPools: ["game"]
  quic:
    alpn: ["hytale/1"]
    cipherSuites: ["TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"]
    cert: config/certs/proxy.crt
    key: config/certs/proxy.key
    clientCa: config/certs/hytale-client-ca.crt
    backendCa: config/certs/backend-ca.crt
    requireClientCert: true
    backendSanAllowlist: ["backend-1.local", "backend-2.local"]
    maxBidirectionalStreams: 100
    maxUnidirectionalStreams: 100
    mtu: 1350
  timeouts:
    handshakeMs: 10000
    idleMs: 30000
  limits:
    handshakesPerMinutePerIp: 60
    concurrentPerIp: 4

auth:
  mode: passthrough
  referral:
    signing:
      algorithm: hmac-sha256
      activeKeyId: "k1"
      keys:
        - keyId: "k1"
          key: env:HYPROX_REFERRAL_HMAC
          scope: backend
          scopeId: lobby-1
          validFrom: 2025-01-01T00:00:00Z
          validTo: 2025-12-31T23:59:59Z
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
  bufferGlobalMaxPackets: 8192
  ticketRequired: true
  ticketMaxAgeSeconds: 10
  ticketSigning:
    algorithm: hmac-sha256
    activeKeyId: k1
    ttlSeconds: 10
    nonceBytes: 16
    keys:
      - keyId: k1
        key: env:HYPROX_MIGRATION_HMAC
        scope: backend
        scopeId: game-1
        validFrom: 2025-01-01T00:00:00Z
        validTo: 2025-12-31T23:59:59Z
  allowPools: ["game"]

observability:
  logging:
    level: info
    redactTokens: true
  tracing:
    enabled: false
    allowlistPacketIds: [0, 10, 18]
    memoryOnly: true
  metrics:
    prometheus:
      enabled: true
      listen: 127.0.0.1:9100

agent:
  auth:
    mode: mtls
    clientCa: config/certs/backend-ca.crt
    sharedKey: env:HYPROX_AGENT_HMAC
  allowlist:
    - backendId: lobby-1
      address: 10.0.0.10
    - backendId: game-1
      address: 10.0.1.10

registry:
  enabled: true
  listen: 127.0.0.1:9200
  maxRequestBytes: 65536
  maxListResults: 200
  rateLimitPerMinute: 120
  allowedPorts: [20001, 20002]
  allowLoopback: false
  allowPublicAddresses: false
  auth:
    mode: mtls
    clientCa: config/certs/orchestrator-ca.crt
    sharedKey: env:HYPROX_REGISTRY_HMAC
    nonceBytes: 16
    clockSkewSeconds: 10
  allowlist:
    - orchestratorId: k8s-eu-1
      address: 10.0.2.10
      allowedPools: ["lobby", "game"]
      allowedBackendIdPrefixes: ["lobby-", "game-"]
  defaults:
    ttlSeconds: 30
    heartbeatGraceSeconds: 10
    drainTimeoutSeconds: 60
  allowedNetworks: ["10.0.0.0/8"]
```

Example config (redirect-only)
```
proxy:
  listen:
    host: 0.0.0.0
    port: 20000
  mode: redirect
  defaultPath: redirect
  quic:
    alpn: ["hytale/1"]
    cipherSuites: ["TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"]
    cert: config/certs/proxy.crt
    key: config/certs/proxy.key
    clientCa: config/certs/hytale-client-ca.crt
    requireClientCert: true
  timeouts:
    handshakeMs: 8000
    idleMs: 20000
  limits:
    handshakesPerMinutePerIp: 60
    concurrentPerIp: 4

# auth, routing, observability, and registry sections match the hybrid example.
```

Example config (full proxy only)
```
proxy:
  listen:
    host: 0.0.0.0
    port: 20000
  mode: full
  defaultPath: full
  quic:
    alpn: ["hytale/1"]
    cipherSuites: ["TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"]
    cert: config/certs/proxy.crt
    key: config/certs/proxy.key
    clientCa: config/certs/hytale-client-ca.crt
    backendCa: config/certs/backend-ca.crt
    requireClientCert: true
    backendSanAllowlist: ["backend-1.local", "backend-2.local"]
  timeouts:
    handshakeMs: 10000
    idleMs: 30000
  limits:
    handshakesPerMinutePerIp: 60
    concurrentPerIp: 4

# auth, routing, migration, observability, and registry sections match the hybrid example.
```

Notes
- Keep `auth.mode: passthrough` for maximum security unless the full proxy must terminate auth to perform a feature.
- Referral payloads must be signed and validated by the target backend to prevent tampering.
- Pin client and backend CAs; do not accept unauthenticated QUIC sessions.
