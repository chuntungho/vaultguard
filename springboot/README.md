# VaultGuard Server (Spring Boot)

Java rewrite of the VaultGuard server. See `docs/superpowers/specs/` for the
requirements, design summary and Phase 1 traceability.

- Java 21, Spring Boot 4.x (Jackson 3, modular test starters)
- Databases: SQLite (default), PostgreSQL, MySQL via `SPRING_PROFILES_ACTIVE`

## Build & test (JVM)

```bash
mvn test              # full suite (H2 + Liquibase)
mvn package           # executable jar in target/
```

Container image (JVM): `docker build -t vaultguard-server .` (uses `Dockerfile`).

## Native image (GraalVM)

Requires GraalVM for JDK 21 (`native-image` on the PATH):

```bash
mvn -Pnative -DskipTests native:compile   # -> target/vaultguard-server
./target/vaultguard-server --spring.profiles.active=sqlite
```

Container image (native): the binary is compiled on the host/CI runner and
packaged with `Dockerfile.native`:

```bash
docker build -f Dockerfile.native -t vaultguard-server:native .
```

## CI

`.github/workflows/springboot-images.yml` runs the JVM test suite, compiles the
native executable (with an `/api/alive` smoke test), and publishes two images
to GitHub Container Registry on pushes to `main`/`feature/rewrite` and `v*` tags:

- `ghcr.io/<owner>/<repo>/server` — native server image (linux/amd64)
- `ghcr.io/<owner>/<repo>/admin-ui` — nginx serving the react-admin bundle,
  proxying `/api/admin` to `BACKEND_URL` (default `http://vaultguard:8080`)

Example compose wiring:

```yaml
services:
  vaultguard:
    image: ghcr.io/<owner>/<repo>/server:main
    volumes: ["vg-data:/app/data"]
  admin-ui:
    image: ghcr.io/<owner>/<repo>/admin-ui:main
    environment:
      BACKEND_URL: http://vaultguard:8080
    ports: ["8081:8080"]
volumes:
  vg-data:
```
