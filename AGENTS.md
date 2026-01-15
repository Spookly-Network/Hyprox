# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java` contains Java sources for the Hyprox plugin (packages under `net.spookly`).
- `src/main/resources/manifest.json` holds plugin metadata.
- `libs/HytaleServer.jar` provides Hytale server APIs (compileOnly).
- `docs/plan/` contains the multi-server proxy planning docs; keep it in sync with implementation.
- `multiserver.md` is a high-level reference for architecture notes.

## Build, Test, and Development Commands
- `./gradlew build` compiles and runs all tests.
- `./gradlew test` runs the JUnit 5 test suite.
- `./gradlew compileJava` compiles sources without running tests.
- `./gradlew clean` removes build outputs.

## Coding Style & Naming Conventions
- Java style: 4-space indentation, braces on the same line, one public class per file.
- Package naming: lower-case, e.g., `net.spookly.hyprox`.
- Class naming: PascalCase (e.g., `Hyprox`, `ProxySession`).
- Keep source files ASCII unless the file already uses Unicode.

## Testing Guidelines
- Framework: JUnit 5 (`org.junit.jupiter`).
- Test location: `src/test/java`.
- Naming: `*Test.java` for unit tests (e.g., `RoutingPolicyTest`).
- Add focused tests for routing, referral signing, and migration state handling as those modules land.

## Commit & Pull Request Guidelines
- Commit messages follow a conventional style seen in history, e.g., `chore: ...`, `feat: ...`, `fix: ...`.
- PRs should include a short summary, testing notes (`./gradlew test`), and links to any related issues.
- If behavior changes, update relevant files in `docs/plan/`.

## Security & Configuration Tips
- Do not commit certificates, private keys, or HMAC secrets; use env vars and local config paths.
- Validate CA bundles and allowlists before enabling dynamic registration or full-proxy auth features.
