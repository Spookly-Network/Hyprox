# Docker setup

Build the image from the repo root:
```
docker build -f infra/Dockerfile -t hyprox:local .
```

Docker Compose (from `infra/`):
```
docker compose up --build
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
- Edit `config/hyprox.yaml` and set `HYPROX_REFERRAL_HMAC` before running again.
- Adjust the published ports to match `proxy.listen.port` and any registry or metrics ports you enable.

Manual certificate creation (host):
```
mkdir -p config/certs
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout config/certs/proxy.key \
  -out config/certs/proxy.crt \
  -days 365 \
  -subj "/CN=hyprox"
```
