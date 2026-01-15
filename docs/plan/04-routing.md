# Routing and pools

Static pools
- Define pools by name, each with a list of backends (host, port, weight, maxPlayers).
- Pool types: lobby, game, shard-<id>.

Routing decisions
- Initial connect: choose pool by config rules (default lobby).
- Transfer: use referral from backend plugin with signed payload.
- Migration: proxy instructs internal handoff (full proxy path).
- Referral routing only applies when signature, TTL, and target backend id are valid; otherwise ignore referral data.
- Use the backend address from configuration; do not trust `hostTo` from unverified referral data.

Health checks
- Passive: connection failures, timeouts.
- Active (optional): periodic ping packet or lightweight QUIC connection check.

Example config (sketch)
```
pools:
  lobby:
    policy: weighted
    backends:
      - host: 10.0.0.10
        port: 9000
        weight: 3
        maxPlayers: 150
      - host: 10.0.0.11
        port: 9000
        weight: 2
        maxPlayers: 150
routes:
  default: lobby
  shardRules:
    - shard: 1
      pool: shard-1
```
