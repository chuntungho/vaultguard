# VaultGuard React Admin UI — Design Spec

**Date:** 2026-05-30
**Branch:** `feature/rewrite`
**Status:** Approved (architecture); pending user review of full spec

## Goal

Rewrite the VaultGuard admin UI as a React single-page application built with
[react-admin](https://marmelab.com/react-admin/) v5. Preserve 1:1 feature
parity with the current static HTML admin (login, users, organizations,
settings, diagnostics) and add the list ergonomics that react-admin gives
for free: per-column sort, pagination, full-text filter, and bulk delete.

The existing static HTML admin under
`springboot/src/main/resources/static/admin/` stays in place during this
project and is removed in a follow-up after the React UI is verified.

## Non-Goals

- No new admin features beyond what the current static admin offers.
- No Create or Edit pages for users or organizations (current admin has none).
- No replacement of the user-facing Bitwarden web vault. This project covers
  the admin UI only.
- No SSR, no Next.js. react-admin is a pure SPA.
- No design-system overhaul — react-admin's default MUI theme is fine.

## Tech Stack

- React 18
- TypeScript 5
- Vite 5 (build + dev server)
- react-admin v5 (resources, list views, auth, data provider plumbing)
- MUI v6 (transitive — react-admin's default UI)
- Vitest + Testing Library (unit + component tests)
- msw (mock service worker, for component tests against the real fetch path)

No additional Maven dependencies on the backend.

## File Map

```
admin-ui/                                         NEW
├── src/
│   ├── App.tsx                                   <Admin> shell, resources, custom routes
│   ├── httpClient.ts                             fetch wrapper, injects X-Admin-Token, raises HttpError
│   ├── dataProvider.ts                           react-admin DataProvider + custom action methods
│   ├── authProvider.ts                           X-Admin-Token + sessionStorage
│   ├── types.ts                                  UserRow, OrgRow, Settings, Diagnostics
│   ├── resources/
│   │   ├── users/
│   │   │   ├── UserList.tsx                      list + row action buttons + bulk delete
│   │   │   └── userActions.ts                    thin wrappers over dataProvider custom methods
│   │   └── organizations/
│   │       └── OrgList.tsx                       list + delete
│   ├── pages/
│   │   ├── LoginPage.tsx                         single token input → validates via diagnostics
│   │   ├── Settings.tsx                          GET/POST /api/admin/settings
│   │   └── Diagnostics.tsx                       read-only display of GET /api/admin/diagnostics
│   └── i18n/
│       └── en.ts                                 RA translation overrides (page titles, action labels)
├── tests/
│   ├── dataProvider.test.ts                      Vitest, fetch mocked
│   ├── authProvider.test.ts
│   ├── httpClient.test.ts
│   └── LoginPage.test.tsx                        msw-backed
├── index.html
├── vite.config.ts                                dev proxy /api/admin → http://localhost:8080
├── tsconfig.json
├── package.json
├── .gitignore
└── README.md                                     run/build/deploy notes

springboot/src/main/java/com/vaultguard/
├── config/
│   ├── VaultGuardProperties.java                 MODIFY: add adminCorsOrigins: List<String>
│   └── SecurityConfig.java                       MODIFY: add CorsConfigurationSource for /api/admin/**
├── service/
│   └── AdminService.java                         MODIFY: listUsers + listOrganizations accept Pageable/filter
└── api/admin/
    └── AdminController.java                      MODIFY: list endpoints accept page/size/sort/q params,
                                                          set X-Total-Count header, expose X-Total-Count via CORS

springboot/src/test/java/com/vaultguard/api/admin/
└── AdminControllerTest.java                      MODIFY: add tests for pagination, sort, filter,
                                                          X-Total-Count, CORS preflight
```

## Architecture

### Process model

- **Dev:** two processes. Spring Boot on `:8080` via `mvn spring-boot:run`;
  Vite dev server on `:5173` via `npm run dev`. Vite's `server.proxy` forwards
  `/api/admin` → `http://localhost:8080`. No CORS encountered in dev.
- **Prod:** two artifacts. Spring Boot jar serves the API; `admin-ui/dist/` is
  a static bundle deployed wherever convenient (nginx, CDN, etc.). The Spring
  Boot app enables CORS on `/api/admin/**` for an allow-listed set of origins
  read from `vaultguard.admin-cors-origins` (default empty = same-origin only).

### Boundary contracts

The React app is organized so that each module has one clear job:

- **`httpClient.ts`** — only place that knows the API base URL and the
  `X-Admin-Token` header. Exposes `httpClient(path, init?)` returning
  `{ status, headers, json }` and throws `HttpError(status, message, body)` on
  non-2xx. Network failures wrap as `HttpError(0, "Network error")`.
- **`dataProvider.ts`** — only place that knows the API URL shape. Implements
  react-admin's `DataProvider` interface for `users` and `organizations`
  resources. Exposes extra action methods (`disableUser`, `enableUser`,
  `deauthUser`, `removeTwoFactor`, `getSettings`, `saveSettings`,
  `getDiagnostics`) for the bespoke admin endpoints.
- **`authProvider.ts`** — only place that knows about `sessionStorage` and
  the token-validation endpoint. Implements react-admin's `AuthProvider`.
- **`resources/*`, `pages/*`** — only consume react-admin's record API and the
  custom action methods. They never touch fetch directly.

### Why this layout

- `admin-ui/` is a top-level peer of `springboot/` and `playwright/`,
  matching the existing repo's separation of independently deployable pieces.
- One file per resource keeps each unit small enough to understand in one
  reading.
- The custom data provider absorbs the API mismatch instead of forcing the
  backend to conform to a third-party REST convention (ra-data-simple-rest,
  ra-data-json-server). The backend stays Bitwarden-shaped.

## Components

### `<Admin>` shell (`App.tsx`)

```tsx
<Admin
  dataProvider={dataProvider}
  authProvider={authProvider}
  loginPage={LoginPage}
  layout={Layout}
  i18nProvider={i18nProvider}
>
  <Resource name="users" list={UserList} />
  <Resource name="organizations" list={OrgList} />
  <CustomRoutes>
    <Route path="/settings" element={<Settings />} />
    <Route path="/diagnostics" element={<Diagnostics />} />
  </CustomRoutes>
</Admin>
```

A custom `Menu` (in `Layout`) adds links to `/settings` and `/diagnostics`
alongside the auto-generated resource links.

### `UserList`

react-admin `<List>` with columns: email, name, enabled, 2FA, cipher count,
attachment count, organization count, created-at. Row actions:

- **Disable / Enable** — toggles based on `enabled` field; calls
  `dataProvider.disableUser(id)` or `enableUser(id)`; refresh via
  `useRefresh()`.
- **Deauth sessions** — confirm dialog, then `deauthUser(id)`.
- **Remove 2FA** — confirm dialog, then `removeTwoFactor(id)`.
- **Delete** — react-admin's built-in `<DeleteButton mutationMode="pessimistic" />`.

List supports per-column sort, page-size selector (25/50/100), full-text
filter via a single search input bound to query param `q`. Bulk delete enabled
via react-admin's default selection UI.

### `OrgList`

react-admin `<List>` with columns: name, billing email, user count, cipher
count, collection count. Row action: Delete (pessimistic). Bulk delete
enabled. Same sort/filter/page behavior as `UserList`.

### `Settings` (custom route)

Standalone form. On mount: `dataProvider.getSettings()` → populate fields
(`domain`, `signupsAllowed`, `invitationsAllowed`, `mail.from`,
`mail.fromName`, `passwordIterations`). Submit: `dataProvider.saveSettings(payload)` →
notification on success/error. Banner reminding that settings are in-memory
only (matches the existing static admin's notice).

### `Diagnostics` (custom route)

Read-only key/value table from `dataProvider.getDiagnostics()`. Refresh button
calls again.

### `LoginPage`

Single password-style input for the admin token. Submit calls
`authProvider.login({ token })`. On reject, shows "Invalid admin token". No
"remember me" — sessionStorage clears on tab close, mirroring the existing
static admin.

## Data contracts

### Backend endpoint changes

`GET /api/admin/users` and `GET /api/admin/organizations` gain these optional
query parameters:

| Param | Type   | Default | Meaning                                      |
|-------|--------|---------|----------------------------------------------|
| page  | int    | 0       | zero-indexed page number                     |
| size  | int    | 25      | page size, capped at 100                     |
| sort  | string | none    | `<field>,<dir>` e.g. `email,asc`; whitelisted fields only |
| q     | string | none    | case-insensitive substring on email/name (users) or name/billingEmail (orgs) |

Response body stays as a JSON array (one page slice). A new response header
`X-Total-Count: <N>` carries the total matching row count. The existing
static HTML ignores the header and the new query params (which all have
sensible defaults), so it keeps working unchanged.

Whitelisted sort fields:

- Users: `email`, `name`, `createdAt`, `enabled`
- Orgs: `name`, `billingEmail`

Unknown sort fields are silently ignored and the default order (existing
`findAll()` order, by primary key) is used.

No changes to `DELETE`, `POST /disable`, `POST /enable`, `POST /deauth`,
`DELETE /2fa`, `GET/POST /settings`, `GET /diagnostics`. They already match
what the React app needs.

### CORS

`SecurityConfig` adds a `CorsConfigurationSource` registered for path
`/api/admin/**`:

- Allowed origins: `vaultguard.admin-cors-origins` (list of full origins,
  default empty)
- Allowed methods: `GET, POST, DELETE, OPTIONS`
- Allowed headers: `X-Admin-Token, Content-Type`
- Exposed headers: `X-Total-Count`
- Allow credentials: false (token is a header, not a cookie)
- Max age: 3600

If `admin-cors-origins` is empty, the CORS filter is wired but rejects all
cross-origin requests — same-origin (the legacy static admin) still works.

### `dataProvider` method map

| react-admin call                           | HTTP                                                   |
|--------------------------------------------|--------------------------------------------------------|
| `getList('users', {pagination, sort, filter})` | `GET /api/admin/users?page=&size=&sort=&q=`            |
| `getList('organizations', …)`              | `GET /api/admin/organizations?page=&size=&sort=&q=`    |
| `getOne('users', {id})`                    | not supported by backend; throws `NotImplemented`      |
| `delete('users', {id})`                    | `DELETE /api/admin/users/{id}`                         |
| `deleteMany('users', {ids})`               | loop `delete` per id; aggregate errors                 |
| `delete('organizations', {id})`            | `DELETE /api/admin/organizations/{id}`                 |
| `deleteMany('organizations', {ids})`       | loop `delete` per id                                   |

react-admin's `<List>` doesn't call `getOne` so the unsupported stub is fine
(no Show/Edit views exist).

Extra (non-DataProvider) methods on the same module:

| Method                          | HTTP                                          |
|---------------------------------|-----------------------------------------------|
| `disableUser(id)`               | `POST /api/admin/users/{id}/disable`          |
| `enableUser(id)`                | `POST /api/admin/users/{id}/enable`           |
| `deauthUser(id)`                | `POST /api/admin/users/{id}/deauth`           |
| `removeTwoFactor(id)`           | `DELETE /api/admin/users/{id}/2fa`            |
| `getSettings()`                 | `GET /api/admin/settings`                     |
| `saveSettings(body)`            | `POST /api/admin/settings`                    |
| `getDiagnostics()`              | `GET /api/admin/diagnostics`                  |

`getList` response shape returned to react-admin:
`{ data: T[], total: parseInt(headers['x-total-count']) }`.

### Sort param encoding

react-admin's `sort` is `{ field: string; order: 'ASC' | 'DESC' }`. The data
provider encodes it as `sort=<field>,<lowercased-order>` (e.g.,
`email,asc`), then the backend parses with `Sort.Order.by(field).with(dir)`.

## Auth flow

`LoginPage` → `authProvider.login({ token })`:

1. `GET /api/admin/diagnostics` with header `X-Admin-Token: <token>`.
2. On 200 → `sessionStorage.setItem("vaultguardAdminToken", token)`, resolve.
3. On non-200 → reject with `new Error("Invalid admin token")`.

`authProvider.checkAuth()` resolves iff `sessionStorage` has the token.

`authProvider.checkError({ status })` rejects on 401 / 403 (react-admin then
redirects to `LoginPage`), resolves otherwise.

`authProvider.logout()` clears the token; react-admin redirects to login.

`authProvider.getIdentity()` returns `{ id: "admin", fullName: "Admin" }` —
needed by react-admin's `<UserMenu>`.

`httpClient` reads the token from `sessionStorage` on every request and
attaches it as `X-Admin-Token`. If no token is present, the request still
goes out (without the header) and the backend's 401 triggers
`checkError` → login redirect. This keeps the client stateless and matches
the existing static admin behavior.

## Error handling

- `httpClient` throws `HttpError(status, message, body)` on any non-2xx
  response and on `fetch` rejection (status 0, "Network error").
- `dataProvider` lets `HttpError` bubble. react-admin's default notification
  system surfaces the message via the snackbar.
- `getList` parses the response: if `X-Total-Count` is missing, falls back to
  `data.length` so older backend versions still render (defensive, low cost).
- Confirm dialogs for destructive actions (deauth, remove-2fa, delete) reuse
  react-admin's `<Confirm>` component. Bulk delete uses the built-in
  confirmation.
- `Settings` save failures show the error message in a notification; the form
  stays editable.

## Testing

### Unit tests (Vitest)

- `httpClient.test.ts`
  - sends `X-Admin-Token` when stored
  - omits the header when not stored
  - parses JSON body for 2xx
  - throws `HttpError` with status + body for non-2xx
  - wraps network rejection as `HttpError(0, "Network error")`

- `dataProvider.test.ts`
  - `getList('users')` sends correct URL with pagination/sort/filter
  - `getList` reads `X-Total-Count` into `total`
  - `getList` falls back to `data.length` when header missing
  - `delete('users', {id})` hits the correct URL
  - each custom method (`disableUser`, etc.) hits the correct URL + method

- `authProvider.test.ts`
  - `login` stores token on 200 diagnostics response
  - `login` rejects + does not store on non-2xx
  - `checkAuth` resolves with token, rejects without
  - `checkError` rejects on 401 / 403, resolves on 500
  - `logout` clears storage

### Component tests (Testing Library + msw)

- `LoginPage.test.tsx`
  - happy path: submit token → redirect indicator
  - error path: submit token → "Invalid admin token" rendered

(Resource-level CRUD is exercised through react-admin's own integration test
suite and through the backend integration tests; we don't re-test
react-admin internals.)

### Backend tests (JUnit, extending `AdminControllerTest`)

- list users with `page`/`size`/`sort`/`q` returns expected slice and order
- `X-Total-Count` header present on list responses and reflects unfiltered count when no `q`, filtered count when `q` set
- unknown sort field is ignored (falls back to default order)
- size capped at 100 when caller requests more
- CORS preflight (`OPTIONS /api/admin/users` with `Origin` matching the
  configured allow-list) returns 200 with `Access-Control-Allow-Origin` +
  `Access-Control-Expose-Headers: X-Total-Count`
- CORS preflight with non-allowed origin returns 403

### Manual smoke test (recorded in `admin-ui/README.md`)

- Start backend, start `npm run dev`, navigate to `http://localhost:5173`
- Log in with the dev admin token from `application-test.properties`
- Verify each resource page loads, filter/sort work, row actions work,
  bulk delete works, settings round-trip, diagnostics displays
- Log out, log in again

## Out of scope (deferred to follow-ups)

- Removal of the existing static HTML admin (separate PR after React UI
  verified in staging)
- Persisting settings to disk (existing limitation, documented in current UI)
- Create / Edit pages for users or organizations (no backend support today)
- A Maven build glue layer (`frontend-maven-plugin`) — the React app is built
  and deployed independently
- Additional languages beyond English (the `i18n/en.ts` file exists only to override react-admin's default English strings — page titles, action labels — not to enable translation)
- Dark mode toggle (react-admin v5 ships a default; configuring it is not in
  the scope of "1:1 parity")
- Production deployment configuration (which host serves `admin-ui/dist/`,
  TLS, etc.) — environment-specific

## Self-Review

**Placeholders:** none. No TBD, TODO, or vague requirements remain.

**Internal consistency:** the file map, architecture, and component sections
agree on module boundaries (httpClient → dataProvider → resources). The
data-contract section's endpoint changes match the Spring Boot files listed
in the file map.

**Scope:** single implementation plan. Backend changes are bounded to two
endpoint signatures + CORS config + matching tests. Frontend is a small Vite
project with eight source files and four test files.

**Ambiguity:** sort param encoding is pinned to `<field>,<lowercased-dir>`;
size cap is 100; CORS default is empty (same-origin only); `getOne` is
explicitly unsupported.

**Risks called out:** none unresolved. CORS misconfiguration would block the
React app in prod — the spec calls out the property name and default
behavior.
