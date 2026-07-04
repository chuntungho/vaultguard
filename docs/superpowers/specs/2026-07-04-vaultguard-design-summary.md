# VaultGuard — Design Summary (Rust reference implementation → Spring Boot rewrite)

**Date:** 2026-07-04
**Companion docs:** `2026-07-04-vaultguard-requirements.md` (requirements),
`2026-03-28-springboot-rewrite-design.md` (rewrite design spec, phases P1–P4),
`2026-07-04-springboot-phase1-gap-analysis.md` (traceability).

---

## 1. Rust reference architecture

```
src/
├── main.rs            # Rocket launch: mounts /, /api, /identity, /icons, /notifications,
│                      # /admin, /events; schedules background jobs; DB migrations
├── config.rs          # CONFIG singleton — every env var / admin-editable setting
├── auth.rs            # JWT encode/decode (login, invite, verify-email, admin, send, 2FA
│                      # remember, register-verify tokens); request guards (Headers,
│                      # OrgHeaders, AdminHeaders…) that authenticate and authorize
├── crypto.rs          # RNG helpers, PBKDF2/HKDF, constant-time eq
├── db/
│   ├── models/        # One model per table (user, device, cipher, folder, favorite,
│   │                  # attachment, two_factor, org, membership, collection, group,
│   │                  # policy, send, emergency_access, event, auth_request, sso…)
│   └── schema.rs      # Diesel schema (sqlite/mysql/postgresql via feature flags)
├── api/
│   ├── identity.rs    # /identity: connect/token (password, refresh, client_credentials,
│   │                  # auth-request), prelogin, register, SSO authorize/callback
│   ├── core/          # /api: accounts, ciphers (incl. attachments), folders, sync,
│   │                  # organizations (incl. groups/policies/members/collections),
│   │                  # two_factor/* (authenticator, email, duo, webauthn, yubikey,
│   │                  # protected_actions), sends, emergency_access, events, public
│   │                  # (Directory Connector), meta (config/alive/now/version, eq-domains,
│   │                  # hibp)
│   ├── admin.rs       # /admin panel API + pages
│   ├── icons.rs       # /icons proxy/cache
│   ├── notifications.rs # /notifications/hub WebSocket (SignalR/MessagePack framing)
│   ├── push.rs        # Bitwarden push relay client
│   └── web.rs         # static web vault + /attachments serving
├── mail.rs            # SMTP + Handlebars templates (verify, invite, new-device, 2FA…)
├── sso.rs/sso_client.rs # OIDC client
└── ratelimit.rs       # Governor-based per-IP limits for login/register/admin
```

Key design decisions visible in the Rust code:

1. **Zero-knowledge storage.** All secret material arrives pre-encrypted; `ciphers.data`
   holds the type-specific JSON blob verbatim. The server only splits out `name`,
   `notes`, `fields`, `password_history` for the response envelope.
2. **Per-user vault state is separated from shared cipher state.** Folder assignment
   (`folders_ciphers`) and favorites (`favorites`) are join tables so an org cipher can
   have per-user folder/favorite without touching the shared row. `/ciphers/<id>/partial`
   exists precisely to mutate this per-user state on read-only ciphers.
3. **Session invalidation via security stamp.** Every access JWT embeds `sstamp`;
   `auth.rs` compares it to `users.security_stamp` on every request. Password change,
   deauthorize-sessions, and admin deauth simply rotate the stamp.
4. **Device = client install.** `devices.uuid` is the *client-generated* device identifier
   sent at login; refresh tokens and 2FA-remember tokens hang off the device row.
5. **2FA data is provider-scoped rows** in `twofactor (user_uuid, atype, enabled, data)`;
   `data` is provider-specific (raw base32 TOTP secret; JSON token state for email).
   Recovery code lives on the user row (`totp_recover`).
6. **Access control for org data** flows from membership type (Owner/Admin/User/Manager),
   `access_all`, and collection ACLs (`users_collections.read_only/hide_passwords`,
   plus groups in P2). Cipher visibility = owned ∪ org-accessible.
7. **Everything is env-configurable**, and a subset is editable at runtime from the admin
   panel (persisted to `config.json`).
8. **Notifications are best-effort**; every mutating endpoint calls `nt.send_*` but clients
   remain correct with polling (`/api/accounts/revision-date`).

## 2. Spring Boot rewrite architecture (springboot/)

Classic layered monolith mirroring the Rust module split (see the 2026-03-28 design spec
for the full rationale):

```
com.vaultguard
├── api/         identity, accounts, devices, twofactor, ciphers (+attachments),
│                folders, sync, organizations, collections, meta, admin
├── auth/        JwtService (Nimbus RS256), JwtAuthenticationFilter, principal
├── config/      VaultGuardProperties (vaultguard.* → env), SecurityConfig (stateless,
│                permit-list mirrors Rust's unauthenticated routes), RateLimitFilter,
│                AdminAuthFilter (X-Admin-Token), RsaKeyConfig (rsa_key.pem, auto-gen)
├── crypto/      PasswordHashService — PBKDF2-SHA256(client hash, per-user salt)
├── db/entity    JPA entities, UUIDs as VARCHAR(36); db/repository Spring Data JPA
├── service/     AuthService (login/2FA/refresh), UserService, CipherService,
│                FolderService, TwoFactorService, SyncService, OrganizationService,
│                AttachmentService, AdminService, MailService (optional SMTP)
└── resources/   Liquibase changelogs (source of schema truth), per-DB profiles
                 (sqlite/postgres/mysql), static admin UI
```

Mapping decisions (Rust → Java):

| Concern | Rust | Spring Boot |
|---|---|---|
| HTTP | Rocket routes + request guards | Spring MVC controllers + Security filters |
| AuthZ context | `Headers`/`OrgHeaders` guards | `@AuthenticationPrincipal` + service-level checks |
| JWT | jsonwebtoken, claims incl. `sstamp` | Nimbus JOSE, same claim set; filter validates sstamp + enabled against DB |
| Schema | Diesel migrations per DB | Liquibase changelog, one logical schema |
| Config | `CONFIG` env singleton | `@ConfigurationProperties("vaultguard")` |
| Mail | lettre + Handlebars | JavaMailSender (optional bean), plain-text templates |
| Rate limit | governor per-IP | in-memory token bucket filter |
| 2FA TOTP | totp-lite | samstevens/totp |
| Background jobs | Rocket tasks + job scheduler | (P3) Spring `@Scheduled` |
| WebSocket | Rocket WS SignalR framing | (P3) Spring WebSocket |

Deliberate P1 simplifications (documented, non-breaking):

- Attachment downloads are served directly by the authenticated
  `GET /api/ciphers/{id}/attachment/{attachmentId}` endpoint instead of Rust's pre-signed
  `/attachments/<cipher>/<id>?token=` URLs; the response `url` points at the Java endpoint.
- `TwoFactor.data` uses small JSON documents for all providers (Rust stores the raw base32
  secret for TOTP); the Java schema is provisioned by Liquibase, so no migration
  compatibility with the Rust DB layout is required.
- Global equivalent-domain groups are served as an empty list (the static
  `global_domains.json` catalog is not yet bundled).
- WebSocket notifications, push relay, icons, sends, emergency access, SSO, groups,
  policies and events are delivered in later phases (P2–P4) per the design spec.

## 3. Data model (P1 scope, as provisioned by Liquibase)

`users`, `devices`, `folders`, `ciphers`, `attachments`, `favorites (user_uuid, cipher_uuid)`,
`twofactor (user_uuid, type)`, `organizations`, `org_memberships`, `collections`,
`collection_ciphers`, `collection_users` — matching the Rust tables of the same purpose
(`favorites` mirrors Rust's per-user favorite join table; folder assignment is currently a
column on `ciphers` because P1 ciphers are single-owner; it must move to a join table when
org sharing lands in P2).

## 4. Security posture

- Stateless bearer-token auth everywhere except `/admin` (token header) and the
  explicitly public endpoints (token, register, prelogin, password-hint,
  verify-email-token, send-email-login, knowndevice, config/alive, static assets).
- Master password never reaches the server in plain form; server re-hashes the client
  hash (PBKDF2-SHA256, per-user salt, constant-time verify).
- Security-stamp claim checked on every authenticated request → instant global logout.
- Rate limiting on `/identity/connect/token` and `/api/accounts/register`.
- Admin API guarded by `X-Admin-Token` and disabled when no token is configured.
