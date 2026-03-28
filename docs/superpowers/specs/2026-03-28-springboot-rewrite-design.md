# VaultGuard Spring Boot Rewrite — Design Spec

**Date:** 2026-03-28
**Status:** Approved
**Goal:** Rewrite the Rust/Rocket Vaultwarden-based server in Spring Boot, maintaining full Bitwarden client API compatibility for production self-hosting.

---

## 1. Stack

| Concern | Choice |
|---------|--------|
| Language | Java 21 |
| Framework | Spring Boot 4.x (Spring Framework 7, Jakarta EE 11) |
| Web | Spring MVC (virtual threads enabled via `spring.threads.virtual.enabled=true`) |
| Security | Spring Security 7.x |
| Data | Spring Data JPA 4.x + Hibernate 7.x |
| JWT | Nimbus JOSE + JWT |
| Build | Maven (single module) |
| Databases | SQLite, PostgreSQL, MySQL/MariaDB |
| Email | Spring Mail (Jakarta Mail) |
| WebSocket | Spring WebSocket (phase 3) |
| Testing | JUnit 5, Mockito, Testcontainers, H2 |

---

## 2. Architecture: Classic Layered Monolith

Single Maven module. Standard `controller → service → repository` layering.

```
src/main/java/com/vaultguard/
├── api/
│   ├── identity/          # /identity/* — login, token, register
│   ├── accounts/          # /api/accounts/*
│   ├── ciphers/           # /api/ciphers/*
│   ├── folders/           # /api/folders/*
│   ├── organizations/     # /api/organizations/*
│   ├── collections/       # /api/collections/*
│   ├── sends/             # /api/sends/*
│   ├── icons/             # /icons/*
│   ├── admin/             # /admin/*
│   └── notifications/     # /notifications/hub (WebSocket, phase 3)
├── auth/                  # JWT filter, token service, RSA key loading
├── config/                # ConfigProperties, SecurityConfig, WebConfig, DB profiles
├── crypto/                # Password hashing, KDF, key derivation utilities
├── db/
│   ├── entity/            # JPA @Entity classes
│   └── repository/        # Spring Data JPA repositories
├── service/               # Business logic (one service class per domain)
├── mail/                  # Email templates and sending
└── util/                  # Shared helpers, UUID generation
```

---

## 3. Data Model

### Phase 1 Entities

| Entity | Key Fields |
|--------|-----------|
| `User` | uuid, email, name, password_hash, kdf_type, kdf_iterations, kdf_memory, kdf_parallelism, private_key, public_key, totp_secret, enabled, verified, stamp_exception |
| `Device` | uuid, user_id, name, type, push_token, refresh_token, twofactor_remember, last_active |
| `Cipher` | uuid, user_id, org_id (nullable), folder_id (nullable), type, data (JSON TEXT), key, reprompt, deleted_date |
| `Folder` | uuid, user_id, name |
| `Attachment` | id, cipher_id, file_name, file_size, akey |
| `Favorite` | user_id + cipher_id (composite PK join table) |
| `TwoFactor` | user_id + type (composite PK), data (JSON TEXT), last_used |
| `Organization` | uuid, name, billing_email, private_key, public_key, plan |
| `OrgMembership` | uuid, user_id, org_id, access_all, atype, status, reset_password_key |
| `Collection` | uuid, org_id, name |
| `CollectionCipher` | collection_id + cipher_id |
| `CollectionUser` | collection_id + org_membership_id, read_only, hide_passwords |

### Phase 2+ Entities (added later)

`Send`, `EmergencyAccess`, `Group`, `GroupUser`, `OrgPolicy`, `Event`, `AuthRequest`, `SsoAuth`, `TwoFactorDuoContext`, `TwoFactorIncomplete`

### Multi-DB Strategy

- UUIDs stored as `VARCHAR(36)` strings across all entities — avoids native UUID type incompatibilities between SQLite, PostgreSQL, and MySQL.
- Hibernate dialect auto-selected via Spring profile: `sqlite`, `postgres`, `mysql`.
- SQLite: `org.xerial:sqlite-jdbc` + community Hibernate dialect (`io.github.willena:hibernate-dialect-sqlite`).
- Schema managed by Liquibase (single changelog with DB-specific changesets where needed).
- Existing `migrations/` SQL files inform the Liquibase changelog but are not reused directly.

---

## 4. Auth & Security

### JWT

- RSA-2048 key pair loaded from disk at startup (same path convention as Vaultwarden: `rsa_key.pem` / `rsa_key.pub.pem`). This ensures token compatibility with existing deployments.
- Access tokens: 1-hour expiry, signed RS256.
- Refresh tokens: 30-day expiry, stored hashed in `Device.refresh_token`.
- Validation via `JwtAuthenticationFilter extends OncePerRequestFilter`.

### Password Hashing

Bitwarden uses client-side key stretching, so the server receives an already-stretched hash. The server applies one additional PBKDF2-SHA256 round (100,000 iterations) server-side before storage. KDF type and parameters (PBKDF2 or Argon2id) are stored per-user and reflected back to the client on login.

### CORS

- `/identity/*` and `/api/*`: permissive (all Bitwarden client origins allowed).
- `/admin/*`: restricted to same-origin or configurable allowlist.

### Rate Limiting

In-memory token-bucket rate limiter (`RateLimitFilter`) applied to:
- `POST /identity/connect/token`
- `POST /api/accounts/register`

Configurable via `application.properties` (`vaultguard.rate-limit.max-requests`, `vaultguard.rate-limit.window-seconds`).

---

## 5. Configuration

All runtime config via `@ConfigurationProperties(prefix = "vaultguard")` backed by `application.properties` / environment variables. Mirrors the `.env.template` variable set from the Rust version for drop-in compatibility (SMTP settings, domain, attachment path, icon service, etc.).

Database profile activated via `SPRING_PROFILES_ACTIVE=sqlite|postgres|mysql`.

---

## 6. Phased Delivery

### Phase 1 — Core Vault (this spec)
- User registration, email verification, login, logout
- Device management, JWT issuance and refresh
- Cipher CRUD (login, card, identity, secure note)
- Folder CRUD
- Attachment upload/download
- Basic 2FA: TOTP (authenticator app) + Email OTP
- Organization creation and basic membership
- Collections + collection-cipher assignment
- Sync endpoint (`GET /api/sync`)

### Phase 2 — Organizations
- Full org membership lifecycle (invite, accept, confirm)
- Groups + group-based collection access
- Org policies
- Admin password reset

### Phase 3 — Advanced Features
- Sends (file and text)
- Emergency access
- WebSocket notification hub (`/notifications/hub`)
- Push notification relay

### Phase 4 — Enterprise & Admin
- SSO / OIDC
- WebAuthn, YubiKey, Duo 2FA
- Admin panel (`/admin`)
- Event logs
- Directory Connector API

---

## 7. Testing Strategy

| Layer | Tool |
|-------|------|
| Unit | JUnit 5 + Mockito — service layer logic, crypto utilities |
| Integration | `@SpringBootTest` + Testcontainers (PostgreSQL, MySQL) — repository and API layers |
| Fast local | H2 in SQLite-compatibility mode for development |
| API compatibility | Existing Playwright suite in `playwright/` run against the Spring Boot server |

All three database backends must pass the integration test suite before any phase is considered complete.

---

## 8. Out of Scope for This Rewrite

- The modified web vault client (served from `bw_web_builds`) — it is served as static files, no changes needed.
- The Rust macros in `macros/` — not applicable to Java.
- Docker image build scripts — will be updated separately once the JAR is buildable.
