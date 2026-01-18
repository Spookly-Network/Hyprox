# Docker setup

Build the image from the repo root:
```
docker build -f infra/Dockerfile -t hyprox:local .
```

Docker Compose (from `infra/`):
```
docker compose up --build
```

Use a published image instead of building locally:
```
HYPROX_IMAGE=yourorg/hyprox:tag docker compose up
```

Run with a config volume mounted at `/config`:
```
docker run --rm -p 20000:20000 -v "$PWD/config:/config" hyprox:local
```

Override the config path if needed:
```
docker run --rm -p 20000:20000 -v "$PWD/config:/config" hyprox:local --config /config/hyprox.yaml
```

Notes:
- On first run, the container generates a self-signed cert in `/config/certs` and a default `hyprox.yaml`, then exits.
- If `HYPROX_REFERRAL_HMAC` is not set, the container generates one at `/config/secret/referral_hmac`
  (override with `HYPROX_REFERRAL_HMAC_FILE`) and exports it for Hyprox.
- If running as root, the entrypoint will chown `/config` to UID/GID 10001 and then drop to the `hyprox` user.
  Set `HYPROX_SKIP_CHOWN=1` to disable the chown step.
- Adjust the published ports to match `proxy.listen.port` and any registry or metrics ports you enable.
- The proxy process runs as `hyprox` (UID/GID 10001). Ensure `/config` is writable if you disable chowning.

Generate a referral signing secret:
```
openssl rand -hex 32
```

Build with your host UID/GID (optional, helps with bind mounts):
```
docker build -f infra/Dockerfile -t hyprox:local --build-arg HYPROX_UID="$(id -u)" --build-arg HYPROX_GID="$(id -g)" .
```

Publish to Docker Hub (from the repo root):
```
docker buildx build -f infra/Dockerfile --platform linux/amd64,linux/arm64 -t yourorg/hyprox:tag --push .
```

Manual certificate creation (host):
```
mkdir -p config/certs
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout config/certs/proxy.key \
  -out config/certs/proxy.crt \
  -days 365 \
  -subj "/CN=hyprox"
```
