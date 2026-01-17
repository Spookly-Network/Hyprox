# Hyprox installation (plain language)

This guide is for people who just want to get the proxy running.

## What you need

- Java 25 (for local runs).
- Docker Desktop (optional, easiest path).
- A few files: proxy TLS cert + key, and a shared HMAC key for referrals.

## Option A: Docker (recommended)

1) Open a terminal in the project root.
2) Start the container once to generate a default config and self-signed certs:
   - `docker compose up --build`
3) Stop the container after it creates `/config/hyprox.yaml`.
4) Edit `config/hyprox.yaml`:
   - Set `proxy.quic.cert` and `proxy.quic.key` to your cert/key paths.
   - Set `HYPROX_REFERRAL_HMAC` in your environment or replace `env:HYPROX_REFERRAL_HMAC` with a real key.
   - Update backend addresses in `routing.pools`.
5) Start again:
   - `docker compose up`

## Option B: Run from the jar

1) Build:
   - `./gradlew build`
2) Run once to generate a default config:
   - `java -jar build/libs/<jar-name>.jar --config config/hyprox.yaml`
3) Edit `config/hyprox.yaml` (same notes as Docker).
4) Run for real:
   - `java -jar build/libs/<jar-name>.jar --config config/hyprox.yaml`

## Option C: Run from Gradle (dev)

1) `./gradlew run --args="--config config/hyprox.yaml"`

## Common setup notes

- The first run writes a default config file and exits. Edit it before running again.
- The container also generates a self-signed cert in `config/certs` if none exists.
- `proxy.mode` controls how clients connect:
  - `redirect`: proxy only redirects clients to a backend.
  - `full`: proxy keeps a live connection and forwards packets.
  - `hybrid`: choose per pool (default path + `fullProxyPools`).
- `auth.mode` defaults to `passthrough`. Use `terminate` only if you need token handling in the proxy.
- The default config expects local backend ports; change them to your servers.

## Where to go next

- Full config reference: `docs/plan/10-config.md`
- Usage details: `docs/plan/15-usage-setup.md`
- Docker notes: `infra/README.md`
