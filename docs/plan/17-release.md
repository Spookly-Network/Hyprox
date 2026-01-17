# Release checklist and versioning

This checklist is for cutting a tagged release of Hyprox.

## Versioning notes

- Use semantic versioning: `MAJOR.MINOR.PATCH`.
- Use `-SNAPSHOT` only for local or CI builds.
- Keep version numbers in sync:
  - `build.gradle` -> `version`
  - `src/main/resources/manifest.json` -> `Version`

## Release checklist

1) Confirm scope and status
   - Review `docs/plan/12-detailed-checklist.md` for any remaining blockers.
   - Ensure `docs/plan/10-config.md` and `docs/plan/15-usage-setup.md` are current.
2) Update versions
   - Set `build.gradle` version to the release number (remove `-SNAPSHOT`).
   - Set `manifest.json` version to the same value.
3) Run tests
   - `./gradlew test`
   - If manual validation is required, capture the steps in the release notes.
4) Build artifacts
   - `./gradlew build`
   - Confirm the output jar and any runtime dependency packaging.
5) Validate runtime assumptions
   - Confirm cert/key paths exist and file permissions are sane.
   - If Docker is used, run `docker compose up --build` with a sample config.
6) Tag and publish
   - Tag the commit (`vMAJOR.MINOR.PATCH`).
   - Publish artifacts and release notes.
   - If publishing Docker images, run `docker buildx build -f infra/Dockerfile --platform linux/amd64,linux/arm64 -t org/hyprox:VERSION --push .`
7) Post-release follow-ups
   - Bump `build.gradle` back to the next `-SNAPSHOT`.
   - Note any follow-up work in `docs/plan/11-next-steps.md`.
