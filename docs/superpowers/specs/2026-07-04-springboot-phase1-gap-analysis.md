# Spring Boot Rewrite — Phase 1 Requirements Traceability & Gap Analysis

**Date:** 2026-07-04
**Requirements source:** `2026-07-04-vaultguard-requirements.md` (derived from the Rust codebase)
**Scope validated:** Phase 1 (Core Vault) as defined in `2026-03-28-springboot-rewrite-design.md`,
plus the admin panel delivered early for the React admin UI.
**Status legend:** ✅ implemented · 🟡 implemented with documented simplification · ⏭ later phase (by design)

## 1. Phase 1 requirement coverage

| Req | Requirement | Status | Notes / Java location |
|---|---|---|---|
| REQ-ID-1 | Password grant + full token response (Key, PrivateKey, Kdf\*, policies, decryption options) | ✅ | `AuthService.tokenResponse` |
| REQ-ID-2 | Refresh grant with per-device token rotation | ✅ | `AuthService.refreshToken` (indexed lookup via `DeviceRepository.findByRefreshToken`) |
| REQ-ID-3 | Login rate limiting per IP | ✅ | `RateLimitFilter` on `/identity/connect/token`, `/api/accounts/register` |
| REQ-ID-4 | Device upsert keyed by client `deviceIdentifier`, stores name/type | ✅ | `AuthService.findOrCreateDevice` (device uuid = client identifier, as in Rust) |
| REQ-ID-5 | 2FA-required error: `TwoFactorProviders` + `TwoFactorProviders2` (+ obscured email), auto-send email code when sole provider | ✅ | `IdentityController` + `TwoFactorService.providerMetadata` |
| REQ-ID-6 | 2FA login: TOTP (0), Email (1), Remember (5), RecoveryCode (8); `twoFactorRemember=1` → `TwoFactorToken` | ✅ | `AuthService.twoFactorAuth`; WebAuthn/Duo/YubiKey are ⏭ P4 |
| REQ-ID-7 | Recovery-code login wipes providers + code | ✅ | `TwoFactorService.useRecoveryCode` |
| REQ-ID-8 | Verified-email login gate (`signups-verify` + SMTP) | ✅ | `AuthService.loginWithPassword`; resend throttling simplified (no resend counter limit) 🟡 |
| REQ-ID-9 | API-key grants (`client_credentials`) | ⏭ P4 | |
| REQ-ID-10 | Register/prelogin under `/identity/accounts/*` too | ✅ | `IdentityController.register/prelogin` aliases; tokenized `register/finish` flow ⏭ P2 |
| REQ-ID-11 | SSO/OIDC | ⏭ P4 | |
| REQ-ID-12 | Full JWT claim set incl. `sstamp`; stamp mismatch rejects the request | ✅ | `JwtService.issueAccessToken(User,Device,clientId)`, checked in `JwtAuthenticationFilter` (also rejects disabled users) |
| REQ-AC-1 | Registration (KDF params, keys, hint; signups toggle) | ✅ | `UserService.register`; domain allowlist/invitations ⏭ P2 |
| REQ-AC-2 | Server-side PBKDF2 re-hash with per-user salt | ✅ | `PasswordHashService` |
| REQ-AC-3 | Prelogin without account enumeration | ✅ | `AccountsController.prelogin` |
| REQ-AC-4 | Profile get/update (PUT + POST), real `TwoFactorEnabled` | ✅ | `AccountsController` |
| REQ-AC-5 | `POST /accounts/keys` | ✅ | `AccountsController.postKeys`; `GET /users/<id>/public-key` ⏭ P2 (needed for sharing) |
| REQ-AC-6 | Verify-email send + token confirm | ✅ | Purpose-scoped JWT (`verifyemail`); purpose tokens are rejected as access tokens |
| REQ-AC-7 | `GET /accounts/revision-date` (epoch ms) | ✅ | |
| REQ-AC-8 | `POST /accounts/password-hint` (no enumeration; hint-in-error when SMTP off) | ✅ | |
| REQ-AC-9 | `POST /accounts/verify-password` → master-password policy | ✅ | |
| REQ-AC-10/11 | Password/KDF change, key rotation, email change, account delete, API keys | ⏭ P2 | Note: security-stamp *checking* is already live, so these become straightforward |
| REQ-DV-1 | `GET /devices/knowndevice` (header-based, base64url email) | ✅ | `DevicesController` |
| REQ-DV-2 | `GET /devices`, `GET /devices/identifier/<id>` | ✅ | |
| REQ-DV-3 | Push tokens / relay | ⏭ P3 | |
| REQ-2F-1 | `GET /two-factor` provider list | ✅ | `TwoFactorController` |
| REQ-2F-2 | TOTP get/activate/disable (32-char base32 secret, code check) | 🟡 | Valid-window matches library default (±1 step); step-reuse tracking (`last_used`) not enforced for TOTP |
| REQ-2F-3 | Recovery code generated on enable, `get-recover` (password-verified) | ✅ | Stored in `users.totp_recover` |
| REQ-2F-4 | `POST/PUT /two-factor/disable` | ✅ | Password-verified (OTP alternative ⏭ P4 protected-actions) |
| REQ-2F-5 | Email 2FA: get-email / send-email / activate / send-email-login | ✅ | Challenge stored as type 1002 then promoted to 1, like Rust; 6-digit single-use token, 10-min expiry; attempt-count limit not enforced 🟡 |
| REQ-2F-6 | Org 2FA policy / device verification | ⏭ P2/P4 | |
| REQ-CI-1 | Cipher list/get with full envelope (type object, favorite, attachments, collectionIds, edit/viewPassword) | ✅ | `CipherResponseMapper` (shared with sync) |
| REQ-CI-2 | Create (`POST /ciphers`, `POST /ciphers/create` with collectionIds), update (PUT/POST + `/admin` aliases) | ✅ | Org ciphers require confirmed membership + ≥1 collection |
| REQ-CI-3 | Soft delete (PUT single/bulk) vs hard delete (DELETE/POST single/bulk, `/admin` aliases) | ✅ | Hard delete cascades attachments/favorites/collection links |
| REQ-CI-4 | Restore single/bulk (+ `/admin`) returning cipher(s) | ✅ | |
| REQ-CI-5 | Move selected to folder (PUT/POST `/ciphers/move`) | ✅ | Folder ownership validated |
| REQ-CI-6 | Partial update (folderId, favorite) | ✅ | |
| REQ-CI-7 | Per-user favorites join table | ✅ | `favorites` table (Liquibase changeset 003), `Favorite` entity |
| REQ-CI-8 | Replace cipher collections (PUT/POST `/collections`, `/collections-admin`) | ✅ | `_v2` variant ⏭ P2 |
| REQ-CI-9 | Bulk import with folder relationships | ✅ | `CipherService.importCiphers` |
| REQ-CI-10 | Purge personal vault (password-verified) | ✅ | Org-vault purge ⏭ P2 |
| REQ-CI-11 | Share to org (single/bulk) | ⏭ P2 | |
| REQ-CI-12 | Archive/unarchive | ⏭ P2 | |
| REQ-CI-13 | Attachment upload/download/delete (legacy protocol) | 🟡 | Direct authenticated download instead of pre-signed URL; v2 upload protocol ⏭ P2; org access honored via `CipherService.isAccessible` |
| REQ-FO-1 | Folder CRUD incl. GET one, POST update alias, POST delete alias | ✅ | `FoldersController` |
| REQ-FO-2 | Folder delete moves ciphers out (never deletes them) | ✅ | `FolderService.delete` |
| REQ-SY-1 | Full sync payload (profile w/ orgs + 2FA state, folders, collections w/ readOnly flags, ciphers incl. trash/favorites/attachments, domains, policies, sends) | 🟡 | Collections report `ReadOnly=false` (per-collection ACLs ⏭ P2); policies/sends empty until their phases |
| REQ-SY-2 | Equivalent-domains settings (GET/POST/PUT) | 🟡 | `SettingsController`; global domain groups served as empty list (catalog not bundled) |
| REQ-OR-1 | Org create (owner membership, key) + get | ✅ | Pre-existing; org keypair, `collectionName` ⏭ P2 |
| REQ-OR-2 | Collections list/create; cipher↔collection linking | ✅ | Per-user collection ACLs ⏭ P2 |
| REQ-OR-3/4/5 | Membership lifecycle, groups, policies, org admin ops | ⏭ P2 | |
| REQ-WV-1 | `/api/config`, `/api/alive`, `/api/now`, `/api/version` (unauthenticated) | ✅ | `MetaController` |
| REQ-AD-1 | Admin API (users, orgs, settings, diagnostics) | ✅ | Pre-existing; deauth now also rotates the security stamp so live tokens die |
| REQ-NF-1 | SQLite/PostgreSQL/MySQL profiles, VARCHAR(36) UUIDs | ✅ | Liquibase changelog |
| REQ-NF-3 | Constant-time verify, RS256 with `rsa_key.pem` | ✅ | |
| REQ-NF-4 | sstamp invalidation, admin token, rate limits, no enumeration | ✅ | Argon2 PHC admin tokens ⏭ P4 |
| REQ-NF-5 | Graceful degradation without SMTP | ✅ | `MailService` optional bean |
| REQ-NF-6 | Scheduled jobs (trash auto-delete, …) | ⏭ P3 | |

## 2. Gaps closed in this change

The following Phase 1 requirements were **missing** from the Java rewrite and are now implemented:

1. **2FA management API** — the entire `/api/two-factor` surface (list, TOTP get/activate/
   disable, email setup/activation, recovery code retrieval, unauthenticated
   `send-email-login`) existed only as an unused service.
2. **2FA login contract** — the error payload lacked `TwoFactorProviders2`/string ids;
   the email code was never auto-sent; Remember (5) and RecoveryCode (8) providers and
   `twoFactorRemember` were unsupported.
3. **Login response** — `PrivateKey`, `Kdf*`, `MasterPasswordPolicy`, `UserDecryptionOptions`,
   `scope`, `ForcePasswordReset` were missing, so real clients could not decrypt after login.
4. **JWT parity + session invalidation** — tokens now carry the Rust claim set including
   `sstamp`, and the auth filter rejects tokens after a stamp rotation or account disable
   (previously admin "deauthorize sessions" did not invalidate access tokens at all).
5. **Device semantics** — devices are now keyed by the client `deviceIdentifier` with the
   human-readable `deviceName` stored (previously the identifier was stored as the name and
   the name discarded); refresh lookup is indexed instead of a full-table scan.
6. **Device endpoints** — `knowndevice`, device list, device by identifier.
7. **Cipher lifecycle** — soft delete (trash) vs hard delete, single + bulk (+ `/admin`
   aliases), restore single/bulk, move-to-folder, partial update, purge, import — none existed;
   `DELETE` was silently hard-deleting without cascading attachments/favorites/links.
8. **Favorites** — new `favorites` join table + entity (was in the design data model but
   never created), wired into create/update/partial and all cipher responses.
9. **Collection assignment** — `PUT/POST /ciphers/<id>/collections(-admin)` and
   org-cipher creation via `POST /ciphers/create` with membership + collection validation.
10. **Cipher response envelope** — type-specific object (`Login`/`Card`/…), parsed `Data`,
    `Favorite`, `Attachments`, `CollectionIds`, `Edit`, `ViewPassword` (shared mapper used by
    both `/api/ciphers` and `/api/sync`).
11. **Account helper endpoints** — `revision-date`, `password-hint`, `verify-password`,
    `verify-email` + `verify-email-token`, `POST /accounts/keys`, `POST /accounts/profile`
    alias, registration under `/identity/accounts/*`, hint storage at registration, real
    `TwoFactorEnabled` in profile.
12. **Email-verification gate** — optional `vaultguard.signups-verify` blocks unverified
    logins and sends verification mail at registration.
13. **Folder parity** — `GET /folders/<id>`, POST update/delete aliases; folder deletion now
    un-files ciphers instead of leaving dangling references.
14. **Client discovery** — `/api/config`, `/api/alive|now|version`, `/api/settings/domains`.

## 3. Remaining deltas (accepted for Phase 1)

- Attachment v2 upload protocol and pre-signed download URLs (current clients fall back to
  the legacy endpoint we serve; revisit in P2).
- TOTP step-reuse tracking and email-2FA attempt limits (library window validation only).
- Global equivalent-domains catalog not bundled (`GlobalEquivalentDomains` is empty).
- Per-collection ACLs (`readOnly`/`hidePasswords`) and membership lifecycle — Phase 2 by design.
- WebSocket notifications, sends, emergency access, events, icons, SSO, WebAuthn/Duo/YubiKey —
  Phases 2–4 per the design spec.
- New-device login notification emails (requires templated mail, P2).

## 4. Verification

- `mvn test`: 63 tests, 0 failures (previously 48), including new integration suites:
  `TwoFactorControllerTest` (setup → login challenge → TOTP login → recovery-code login),
  `CipherLifecycleTest` (soft-delete/restore, bulk, move, folder-delete un-filing,
  favorites, import, purge), `DevicesAndMetaTest` (knowndevice lifecycle, config,
  account helpers, verify-email token, security-stamp invalidation).
- One pre-existing test updated: hard-delete now returns 200 with empty body (the Rust
  `EmptyResult` contract) instead of 204.
