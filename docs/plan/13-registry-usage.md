# Registry usage (orchestrators)

This guide describes how orchestrators register, heartbeat, drain, and list backends in the
registry control plane.

## Authentication (HMAC)

Registry requests use HMAC-SHA256 headers. The registry currently accepts `auth.mode: hmac`.

Required headers:
- `X-Hyprox-Orchestrator`: orchestrator id.
- `X-Hyprox-Timestamp`: unix epoch seconds.
- `X-Hyprox-Nonce`: unique nonce per request.
- `X-Hyprox-Signature`: base64(HMAC_SHA256(canonical, sharedKey)).

Canonical string format (newline separated):
```
METHOD
PATH_WITH_QUERY
TIMESTAMP
NONCE
BODY
```

Notes:
- `PATH_WITH_QUERY` must match the raw request path (including query string when present).
- `BODY` is the exact JSON payload (empty for GET).
- Nonces are checked for replay using `orchestratorId:nonce`.
- Timestamps are rejected when outside `registry.auth.clockSkewSeconds`.

Example signing snippet (bash + openssl):
```bash
method="POST"
path="/v1/registry/register"
timestamp="$(date +%s)"
nonce="$(uuidgen | tr -d '-' | cut -c1-16)"
body='{"orchestratorId":"orch-1","pool":"lobby","backendId":"lobby-1","host":"10.0.0.10","port":9000}'
canonical="${method}\n${path}\n${timestamp}\n${nonce}\n${body}"
signature="$(printf '%s' "$canonical" | openssl dgst -sha256 -hmac "$HYPROX_REGISTRY_SHARED_KEY" -binary | base64)"
```

## Endpoints

### POST /v1/registry/register

Body:
```
{
  "orchestratorId": "orch-1",
  "pool": "lobby",
  "backendId": "lobby-1",
  "host": "10.0.0.10",
  "port": 9000,
  "weight": 1,
  "maxPlayers": 150,
  "tags": ["lobby", "eu"],
  "ttlSeconds": 30
}
```

Notes:
- `ttlSeconds` is capped to `registry.defaults.ttlSeconds`.
- `host` must pass allowed network, loopback/public checks, and SAN allowlist rules.
- `port` must be in `registry.allowedPorts`.

Curl example:
```bash
curl -X POST "http://REGISTRY_HOST:REGISTRY_PORT/v1/registry/register" \
  -H "Content-Type: application/json" \
  -H "X-Hyprox-Orchestrator: orch-1" \
  -H "X-Hyprox-Timestamp: $timestamp" \
  -H "X-Hyprox-Nonce: $nonce" \
  -H "X-Hyprox-Signature: $signature" \
  -d "$body"
```

### POST /v1/registry/heartbeat

Body:
```
{
  "orchestratorId": "orch-1",
  "backendId": "lobby-1",
  "ttlSeconds": 30
}
```

Notes:
- `ttlSeconds` is capped to `registry.defaults.ttlSeconds`.

### POST /v1/registry/drain

Body:
```
{
  "orchestratorId": "orch-1",
  "backendId": "lobby-1",
  "drainSeconds": 60
}
```

Notes:
- `drainSeconds` is capped to `registry.defaults.drainTimeoutSeconds`.

### GET /v1/registry/backends

Query parameters:
- `pool`: optional pool filter.
- `limit`: max results (capped to `registry.maxListResults`).
- `offset`: pagination offset.

Example:
```bash
path="/v1/registry/backends?pool=lobby&limit=50&offset=0"
```

## Allowlist constraints

Orchestrators must match the `registry.allowlist` by `orchestratorId` and (optional) source
address. Requests are also gated by `registry.allowedNetworks`, allowed pools, and backend id
prefix rules.
