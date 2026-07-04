# VaultGuard — Requirements Summary (derived from the Rust codebase)

**Date:** 2026-07-04
**Source of truth:** `src/` (Rust/Rocket implementation, a Vaultwarden fork)
**Purpose:** Enumerate the functional and non-functional requirements the server must satisfy,
as implemented by the Rust codebase, so the Spring Boot rewrite (`springboot/`) can be validated
against them. Each requirement is tagged with the delivery phase from
`2026-03-28-springboot-rewrite-design.md` (P1–P4).

---

## 1. Product overview

VaultGuard is a self-hosted, API-compatible alternative implementation of the Bitwarden server.
All vault content is end-to-end encrypted by the clients; the server stores and serves opaque
ciphertext, enforces access control, and implements the Bitwarden client REST API:

- `/identity/*` — OAuth2-style token issuance, registration, prelogin, SSO
- `/api/*` — core vault API (accounts, ciphers, folders, organizations, sync, 2FA, sends, …)
- `/notifications/hub` — WebSocket change notifications
- `/icons/*` — website icon proxy/cache
- `/admin/*` — server admin panel
- `/` — static web vault

## 2. Functional requirements

### 2.1 Identity & sessions (P1, SSO parts P4)

- **REQ-ID-1** `POST /identity/connect/token` supports `grant_type=password`:
  validates email + client-stretched master-password hash, rejects disabled users,
  and returns `access_token` (RS256 JWT, 1h default), `refresh_token`, `expires_in`,
  `token_type=Bearer`, `scope`, plus account decryption material: `Key` (user symmetric key),
  `PrivateKey`, `Kdf`, `KdfIterations`, `KdfMemory`, `KdfParallelism`,
  `ForcePasswordReset`, `ResetMasterPassword`, `MasterPasswordPolicy`, and
  `UserDecryptionOptions`. Failed logins return OAuth `invalid_grant` errors.
- **REQ-ID-2** `grant_type=refresh_token` rotates the device refresh token and issues a
  new access token. Refresh tokens are stored per device.
- **REQ-ID-3** Logins are rate-limited per client IP (token bucket).
- **REQ-ID-4** Each login upserts a **device** record keyed by the client-supplied
  `deviceIdentifier`, storing `deviceName` and `deviceType`, updating last-activity.
- **REQ-ID-5** When the account has enabled 2FA providers and no valid `twoFactorToken` is
  supplied, the token endpoint returns HTTP 400 with `error=invalid_grant`,
  `TwoFactorProviders` (list of provider ids as strings) and `TwoFactorProviders2`
  (map of provider id → provider metadata, e.g. obscured email for provider 1).
  If email (1) is the only provider, the login email code is sent automatically.
- **REQ-ID-6** 2FA login accepts provider-specific tokens: TOTP (0), Email (1) [P1];
  Duo (2), YubiKey (3), WebAuthn (7) [P4]; Remember (5) and RecoveryCode (8) are always
  accepted when applicable. `twoFactorRemember=1` returns a `TwoFactorToken` bound to the
  device that bypasses 2FA on subsequent logins from that device.
- **REQ-ID-7** Logging in with the recovery code (provider 8) removes all of the user's 2FA
  providers and clears the stored recovery code.
- **REQ-ID-8** If email verification is required (`SIGNUPS_VERIFY` with mail enabled) an
  unverified user cannot log in; a verification email is (re)sent subject to resend
  limits. (P1)
- **REQ-ID-9** `grant_type=client_credentials` supports user API keys
  (`scope=api`, `client_id=user.<uuid>`, `client_secret=<api_key>`) and organization API
  keys (`scope=api.organization`). (P4)
- **REQ-ID-10** Registration endpoints exist under both trees:
  `POST /identity/accounts/register`, `/identity/accounts/register/send-verification-email`,
  `/identity/accounts/register/finish`, and prelogin at `/identity/accounts/prelogin`
  as well as `/api/accounts/prelogin`. (P1; the tokenized `register/finish` flow P2)
- **REQ-ID-11** SSO/OIDC: `GET /identity/sso/prevalidate`, `GET /identity/connect/authorize`,
  `GET /identity/connect/oidc-signin`. (P4)
- **REQ-ID-12** Access JWTs carry: `iss` (`<domain>/|login`), `sub` (user uuid), `nbf`, `exp`,
  `premium`, `name`, `email`, `email_verified`, `sstamp` (security stamp), `device`,
  `devicetype`, `client_id`, `scope=["api","offline_access"]`, `amr=["Application"]`.
  Requests bearing a JWT whose `sstamp` no longer matches the user's stored security stamp
  are rejected — changing the stamp invalidates all sessions (deauthorize). (P1)

### 2.2 Registration & prelogin (P1)

- **REQ-AC-1** `POST /api/accounts/register` creates a user from
  `email`, `name`, `masterPasswordHash`, `key`, `keys{publicKey, encryptedPrivateKey}`,
  KDF settings (`kdf`, `kdfIterations`, `kdfMemory`, `kdfParallelism`), optional
  `masterPasswordHint`. Signups can be disabled (`SIGNUPS_ALLOWED`) or restricted to
  domain allowlists; invited users may always register.
- **REQ-AC-2** The server never stores the raw client hash: it applies a server-side
  PBKDF2-SHA256 round with per-user random salt before persisting.
- **REQ-AC-3** `POST /api/accounts/prelogin` returns `{kdf, kdfIterations, kdfMemory,
  kdfParallelism}` for the email; defaults are returned for unknown emails
  (no account enumeration).

### 2.3 Account management

- **REQ-AC-4** `GET /api/accounts/profile`, `PUT|POST /api/accounts/profile` — read and
  update `name`/`masterPasswordHint`; the profile JSON includes id, email,
  `emailVerified`, `key`, `privateKey`, `securityStamp`, `twoFactorEnabled` (actual state),
  premium flags, culture, `object=profile`, and organization memberships. (P1)
- **REQ-AC-5** `POST /api/accounts/keys` stores the RSA keypair; `GET /users/<uuid>/public-key`
  serves another user's public key. (P1)
- **REQ-AC-6** `POST /api/accounts/verify-email` sends a signed verification link;
  `POST /api/accounts/verify-email-token` (unauthenticated) validates `{userId, token}` and
  marks the account verified. (P1)
- **REQ-AC-7** `GET /api/accounts/revision-date` returns the user's last revision as epoch
  milliseconds (used by clients to decide whether to sync). (P1)
- **REQ-AC-8** `POST /api/accounts/password-hint` (unauthenticated) emails the hint when mail
  is enabled, or returns it in the error message when `SHOW_PASSWORD_HINT` is set; behaves
  identically for unknown emails to prevent enumeration. (P1)
- **REQ-AC-9** `POST /api/accounts/verify-password` validates the master password hash and
  returns the applicable master-password policy. (P1)
- **REQ-AC-10** Password/KDF lifecycle: `POST /api/accounts/password`, `POST /api/accounts/kdf`,
  `POST /api/accounts/key-management/rotate-user-account-keys`, `POST /api/accounts/security-stamp`
  — change credentials, rotate keys, reset the security stamp (invalidating sessions and
  deleting devices). (P2)
- **REQ-AC-11** Email change (`email-token`, `email`), account delete (`delete`,
  `delete-recover`, `delete-recover-token`), avatar, `set-password` (SSO-created accounts),
  personal API key (`api-key`, `rotate-api-key`). (P2/P4)

### 2.4 Devices (P1; push P3)

- **REQ-DV-1** `GET /api/devices/knowndevice` — headers `X-Device-Identifier` and
  `X-Request-Email` (base64url email) → boolean "device already known".
- **REQ-DV-2** `GET /api/devices` lists the user's devices (`id`, `name`, `type`,
  `identifier`, `creationDate`, `isTrusted`); `GET /api/devices/identifier/<id>` returns one.
- **REQ-DV-3** Push-token registration/clearing endpoints and push relay. (P3)

### 2.5 Two-factor authentication (TOTP + Email = P1; WebAuthn/Duo/YubiKey = P4)

- **REQ-2F-1** `GET /api/two-factor` lists enabled providers
  (`{enabled, type, object:"twoFactorProvider"}`).
- **REQ-2F-2** `POST /api/two-factor/get-authenticator` (password- or OTP-verified) returns the
  existing or a freshly generated 20-byte base32 TOTP secret; `POST|PUT /api/two-factor/authenticator`
  validates a current TOTP against the submitted key and enables the provider;
  DELETE disables it. TOTP validation accepts a ±1 time-step window and rejects reuse of the
  same step.
- **REQ-2F-3** Enabling any 2FA provider generates a one-time **recovery code** (base32) if the
  user has none. `POST /api/two-factor/get-recover` returns it (password-verified).
- **REQ-2F-4** `POST /api/two-factor/disable` / `PUT` disables a provider after password/OTP
  verification.
- **REQ-2F-5** Email 2FA: `POST /api/two-factor/get-email` shows config;
  `POST /api/two-factor/send-email` stores a pending challenge (type 1002) for an arbitrary
  destination email and sends a token; `PUT /api/two-factor/email` verifies the token and
  activates provider 1; `POST /api/two-factor/send-email-login` (unauthenticated;
  email + master-password hash) sends the login token. Tokens are 6+ digits, single-use,
  expire, and have limited attempts.
- **REQ-2F-6** Org-level 2FA policy enforcement, device-verification settings, protected
  actions OTP. (P2/P4)

### 2.6 Vault items — ciphers (P1 unless noted)

- **REQ-CI-1** `GET /api/ciphers` lists all ciphers the user can access (owned + via org
  membership/collections); `GET /api/ciphers/<id>` (and `/details`, `/admin`) returns one.
  Cipher JSON includes: `id`, `type` (1 Login, 2 SecureNote, 3 Card, 4 Identity),
  `name`, `notes`, `fields`, type-specific object (`login`/`card`/`secureNote`/`identity`),
  legacy merged `data`, `folderId`, `organizationId`, `favorite` (per-user),
  `reprompt`, `key`, `attachments`, `collectionIds`, `edit`, `viewPassword`,
  `revisionDate`, `creationDate`, `deletedDate`, `object`.
- **REQ-CI-2** `POST /api/ciphers` creates a personal cipher;
  `POST /api/ciphers/create` accepts `{cipher, collectionIds}` and is required for
  organization-owned ciphers (must target ≥1 writable collection);
  `PUT|POST /api/ciphers/<id>` updates (with `/admin` variants for org managers).
- **REQ-CI-3** Soft delete vs hard delete: `PUT /api/ciphers/<id>/delete` (and bulk
  `PUT /api/ciphers/delete`) set `deletedDate` (trash); `DELETE /api/ciphers/<id>`,
  `POST /api/ciphers/<id>/delete`, bulk `DELETE /api/ciphers` / `POST /api/ciphers/delete`
  permanently delete (including attachments). `/admin` variants exist for org ciphers.
- **REQ-CI-4** Restore from trash: `PUT /api/ciphers/<id>/restore`,
  bulk `PUT /api/ciphers/restore` (+ `/admin` variants); restore clears `deletedDate` and
  returns the cipher (bulk returns a list).
- **REQ-CI-5** `PUT|POST /api/ciphers/move` moves selected cipher ids into a folder
  (`folderId` null = no folder); the folder must belong to the user.
- **REQ-CI-6** `PUT|POST /api/ciphers/<id>/partial` updates only per-user state
  (`folderId`, `favorite`) and works on read-only shared ciphers.
- **REQ-CI-7** Favorites are per-user (join table), settable at create/update via
  `favorite: true` and via `/partial`.
- **REQ-CI-8** `PUT|POST /api/ciphers/<id>/collections` (+ `/collections-admin`,
  `/collections_v2`) replaces the set of collections a cipher is assigned to; only
  collections the user can access are affected.
- **REQ-CI-9** `POST /api/ciphers/import` imports `{folders, ciphers, folderRelationships}`
  in bulk, creating folders and linking ciphers by index.
- **REQ-CI-10** `POST /api/ciphers/purge` (password-verified) deletes the personal vault
  (or, with `?organizationId=`, an org vault, owner only).
- **REQ-CI-11** Sharing: `PUT|POST /api/ciphers/<id>/share`, bulk `PUT /api/ciphers/share`
  move personal ciphers into an organization with collection assignment. (P2)
- **REQ-CI-12** Archive/unarchive endpoints (`/archive`, `/unarchive`, bulk). (P2)
- **REQ-CI-13** Attachments: legacy `POST /api/ciphers/<id>/attachment` (multipart `data`,
  per-attachment encrypted `key`), download via
  `GET /api/ciphers/<id>/attachment/<attachment_id>` (Rust serves a pre-signed
  `/attachments/...` URL; a direct authenticated download is acceptable),
  delete via `DELETE` (+ POST alias + `/admin` variants). The v2 protocol
  (`POST /attachment/v2` then upload to `/attachment/<id>`) is used by current clients;
  size limits per user/org are enforced. (P1 for upload/download/delete)

### 2.7 Folders (P1)

- **REQ-FO-1** `GET /api/folders`, `GET /api/folders/<id>`, `POST /api/folders`,
  `PUT|POST /api/folders/<id>`, `DELETE /api/folders/<id>` +
  `POST /api/folders/<id>/delete`. Folder JSON: `{id, name, revisionDate, object:"folder"}`.
- **REQ-FO-2** Deleting a folder moves its ciphers out of the folder (never deletes them).

### 2.8 Sync (P1)

- **REQ-SY-1** `GET /api/sync` returns the full account state in one payload:
  `profile` (incl. organization memberships and actual `twoFactorEnabled`), `folders`,
  `collections` (with per-user `readOnly`/`hidePasswords`), `ciphers` (including trashed
  ones with `deletedDate` set, with per-user favorite flags and attachments),
  `domains` (equivalent domains; omitted when `excludeDomains=true`), `policies`, `sends`,
  `object:"sync"`.
- **REQ-SY-2** `GET|POST|PUT /api/settings/domains` manages the user's equivalent domains
  and excluded global domain groups.

### 2.9 Organizations & collections (create/membership basics = P1; lifecycle = P2)

- **REQ-OR-1** `POST /api/organizations` creates an org (`name`, `billingEmail`, `key`,
  optional initial `collectionName`) with the creator as confirmed Owner; org keypair
  supported. `GET /api/organizations/<id>` returns org details (Owner/Admin).
- **REQ-OR-2** Collections: list (`GET /api/organizations/<org>/collections`, `/details`),
  create, update, delete (+ bulk), `GET .../users` and user assignment with per-collection
  `readOnly`/`hidePasswords`. Ciphers link to collections many-to-many. (list/create = P1)
- **REQ-OR-3** Membership lifecycle: invite → accept → confirm with per-member type
  (Owner 0 / Admin 1 / User 2 / Manager 3), `accessAll`, revoke/restore, edit, delete,
  bulk operations, org user public keys. (P2)
- **REQ-OR-4** Groups, org policies (incl. master-password policy aggregation exposed at
  login), admin password reset, org import, org export, plans/billing metadata stubs,
  Directory Connector public API. (P2/P4)
- **REQ-OR-5** Org vault purge, leave org, delete org. (P2)

### 2.10 Sends (P3)

- **REQ-SE-1** Text/file Sends with access-count limits, expiry, optional password,
  anonymous access page (`/#/send/...` + `/api/sends/access/...`), upload v2 flow,
  per-user/global limits and `SENDS_ALLOWED` policy.

### 2.11 Emergency access (P3)

- **REQ-EA-1** Grantor/grantee invitations, takeover/view flows, wait-time policy,
  recurring reminder/expiry jobs.

### 2.12 Events (P2/P4)

- **REQ-EV-1** Org/user event log capture and query endpoints (`/api/organizations/<id>/events`,
  `/api/ciphers/<id>/events`, provider endpoints), retention cleanup job.

### 2.13 Notifications (P3)

- **REQ-NO-1** WebSocket hub at `/notifications/hub` (+ anonymous hub) broadcasting
  SignalR-encoded change messages (cipher/folder/user/send/auth-request updates) so other
  sessions refresh live; falls back to polling `revision-date`.
- **REQ-NO-2** Mobile push relay (Bitwarden push endpoints) when configured.

### 2.14 Icons & web vault (P1-adjacent infrastructure)

- **REQ-IC-1** `GET /icons/<domain>/icon.png` serves (optionally proxied/cached) website
  icons; configurable service, redirect modes, blacklists.
- **REQ-WV-1** The server serves the patched Bitwarden web vault as static files at `/`,
  plus `/api/config` (client capability/feature discovery: version string, server name/url,
  environment endpoints, feature states) and liveness endpoints
  (`/api/alive`, `/api/now`, `/api/version`).

### 2.15 Admin panel (P4; implemented early for the React admin UI)

- **REQ-AD-1** Token-protected admin API: list/delete/disable/enable users, deauthorize
  sessions, remove 2FA, list/delete organizations, view/save settings, diagnostics,
  invite user, SMTP test, backup (SQLite).

## 3. Non-functional requirements

- **REQ-NF-1 Databases:** SQLite, PostgreSQL, MySQL/MariaDB behind one schema; UUIDs stored
  as 36-char strings.
- **REQ-NF-2 Config:** every behavior toggle via environment variables / config file
  (`.env.template` is the catalog); domain must be configured for correct URL generation.
- **REQ-NF-3 Crypto:** server-side PBKDF2-SHA256 (default 600k client / 100k+ server-side
  re-hash in the Java port; the Rust server re-hashes with per-user salt and configurable
  `PASSWORD_ITERATIONS`), constant-time comparisons, RSA-2048 RS256 JWTs with keys
  auto-generated/loaded from `rsa_key.pem`.
- **REQ-NF-4 Security:** security-stamp session invalidation, admin token (plain or Argon2
  PHC), rate limiting on login/register/admin, no account enumeration on
  prelogin/password-hint/register, CORS restricted appropriately, HaveIBeenPwned proxy.
- **REQ-NF-5 Mail:** SMTP optional; when disabled the server must degrade gracefully
  (no verification requirement, hints shown inline, invites auto-accepted).
- **REQ-NF-6 Jobs:** scheduled maintenance (trash auto-delete, incomplete-2FA login alerts,
  send/emergency-access expiry, event retention).
- **REQ-NF-7 Observability:** structured logging, optional query logging, diagnostics page.

## 4. Explicit non-goals of the server

- The server never sees plaintext vault data or the master password (zero-knowledge).
- Billing/licensing is stubbed (everything reports as premium/self-hosted-free).
