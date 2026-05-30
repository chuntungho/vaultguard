# VaultGuard Admin UI

React + react-admin v5 admin panel for VaultGuard.

## Develop

```bash
# in springboot/, start the backend
mvn spring-boot:run

# in admin-ui/
npm install
npm run dev
```

The Vite dev server runs at http://localhost:5173. The dev server proxies
`/api/admin` to `http://localhost:8080`, so CORS does not apply locally.

Use the admin token from `springboot/src/main/resources/application.properties`
(`vaultguard.admin-token`) to log in.

## Build

```bash
npm run build
```

Output: `admin-ui/dist/`. Deploy these static files behind any HTTP server
(nginx, S3, etc.). The deployed origin must be listed in
`vaultguard.admin-cors-origins` on the backend.

## Test

```bash
npm test
```

Runs Vitest unit + component tests. Component tests use msw to mock the
backend; no backend process is required.

## Smoke test (manual)

After `npm run build` and deploying `dist/`, with the backend running:

1. Open the deployed URL.
2. Enter the admin token; the login page should redirect to `/users`.
3. Verify each list page loads, filter/sort work, row actions work, bulk
   delete works, settings round-trip, diagnostics displays.
4. Sign out; verify redirect back to login.
