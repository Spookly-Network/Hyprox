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
- The container expects `config/hyprox.yaml` to exist in the mounted directory.
- Adjust the published ports to match `proxy.listen.port` and any registry or metrics ports you enable.
