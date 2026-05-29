# React Admin UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static HTML admin UI with a react-admin v5 SPA in a new top-level `admin-ui/` folder, preserving 1:1 feature parity (login, users, organizations, settings, diagnostics) and adding list ergonomics (filter, sort, pagination, bulk delete).

**Architecture:** Two deployable artifacts — Spring Boot jar (unchanged path/serves API) and `admin-ui/dist/` (static Vite bundle, deployed independently). Backend gets minor changes: pagination/sort/filter query params on the two list endpoints, an `X-Total-Count` response header, and a CORS filter on `/api/admin/**` with a configurable origin allow-list. Legacy static admin pages stay in place during this project.

**Tech Stack:** Java 21, Spring Boot 3.4.4 (existing); Vite 5, React 18, TypeScript 5, react-admin v5, MUI v6 (transitive), Vitest, Testing Library, msw.

**Spec:** `docs/superpowers/specs/2026-05-30-react-admin-ui-design.md`

---

## File Map

```
admin-ui/                                         NEW (all created in this plan)
├── src/
│   ├── App.tsx                                   <Admin> shell, resources, custom routes
│   ├── main.tsx                                  ReactDOM bootstrap
│   ├── httpClient.ts                             fetch wrapper, X-Admin-Token, HttpError
│   ├── dataProvider.ts                           DataProvider + custom action methods
│   ├── authProvider.ts                           X-Admin-Token + sessionStorage
│   ├── types.ts                                  UserRow, OrgRow, Settings, Diagnostics
│   ├── resources/
│   │   ├── users/UserList.tsx                    list + row action buttons + bulk delete
│   │   └── organizations/OrgList.tsx             list + delete + bulk delete
│   ├── pages/
│   │   ├── LoginPage.tsx                         single token input → validates
│   │   ├── Settings.tsx                          GET/POST /api/admin/settings
│   │   └── Diagnostics.tsx                       GET /api/admin/diagnostics
│   ├── Layout.tsx                                custom Layout with Menu including settings/diagnostics
│   └── i18n.ts                                   English title overrides
├── tests/
│   ├── httpClient.test.ts
│   ├── dataProvider.test.ts
│   ├── authProvider.test.ts
│   └── LoginPage.test.tsx                        msw-backed component test
├── index.html
├── vite.config.ts
├── vitest.config.ts
├── tsconfig.json
├── tsconfig.node.json
├── package.json
├── .gitignore
└── README.md

springboot/src/main/java/com/vaultguard/
├── config/
│   ├── VaultGuardProperties.java                 MODIFY: add adminCorsOrigins field
│   └── SecurityConfig.java                       MODIFY: enable CORS + CorsConfigurationSource bean
├── service/
│   └── AdminService.java                         MODIFY: listUsers + listOrganizations accept page/size/sort/q,
│                                                          return AdminPage (data + total)
└── api/admin/
    └── AdminController.java                      MODIFY: list endpoints take query params, set X-Total-Count

springboot/src/main/java/com/vaultguard/service/
└── AdminPage.java                                CREATE: record AdminPage<T>(List<T> data, long total)

springboot/src/test/resources/
└── application-test.properties                   MODIFY: add vaultguard.admin-cors-origins=http://localhost:5173

springboot/src/test/java/com/vaultguard/api/admin/
└── AdminControllerTest.java                      MODIFY: pagination/sort/filter/X-Total-Count/CORS tests
```

---

## Task 1: Backend — VaultGuardProperties: add `adminCorsOrigins`

**Files:**
- Modify: `springboot/src/main/java/com/vaultguard/config/VaultGuardProperties.java`
- Modify: `springboot/src/test/java/com/vaultguard/config/VaultGuardPropertiesTest.java`
- Modify: `springboot/src/test/resources/application-test.properties`

- [ ] **Step 1: Add test asserting `adminCorsOrigins` defaults to empty list**

Open `springboot/src/test/java/com/vaultguard/config/VaultGuardPropertiesTest.java` and add this test method inside the existing class (after any existing tests):

```java
@Test
void adminCorsOriginsDefaultsToEmptyList() {
    assertThat(props.getAdminCorsOrigins()).isNotNull();
    assertThat(props.getAdminCorsOrigins()).isEmpty();
}

@Test
void adminCorsOriginsBindsFromTestProperties() {
    // application-test.properties sets vaultguard.admin-cors-origins=http://localhost:5173
    assertThat(props.getAdminCorsOrigins()).contains("http://localhost:5173");
}
```

If `assertThat` / `org.assertj.core.api.Assertions.assertThat` isn't imported yet, add:

```java
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test -Dtest=VaultGuardPropertiesTest 2>&1 | tail -15
```

Expected: `adminCorsOriginsDefaultsToEmptyList` and `adminCorsOriginsBindsFromTestProperties` fail with "method getAdminCorsOrigins not found" or compilation error.

- [ ] **Step 3: Add the property to VaultGuardProperties**

Open `springboot/src/main/java/com/vaultguard/config/VaultGuardProperties.java`. Add the import at the top of the imports block:

```java
import java.util.ArrayList;
import java.util.List;
```

Add the field after the `private String webVaultPath = "";` line (around line 17):

```java
    private List<String> adminCorsOrigins = new ArrayList<>();
```

Add the getter/setter pair before the closing brace of the class (after `setWebVaultPath`):

```java
    public List<String> getAdminCorsOrigins() { return adminCorsOrigins; }
    public void setAdminCorsOrigins(List<String> v) { this.adminCorsOrigins = v; }
```

- [ ] **Step 4: Add the property to the test profile**

Open `springboot/src/test/resources/application-test.properties` and add this line at the end:

```properties
vaultguard.admin-cors-origins=http://localhost:5173
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test -Dtest=VaultGuardPropertiesTest 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add springboot/src/main/java/com/vaultguard/config/VaultGuardProperties.java springboot/src/test/java/com/vaultguard/config/VaultGuardPropertiesTest.java springboot/src/test/resources/application-test.properties && git commit -m "feat: add vaultguard.admin-cors-origins property"
```

---

## Task 2: Backend — Pagination/Sort/Filter on `/api/admin/users` + `X-Total-Count`

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/service/AdminPage.java`
- Modify: `springboot/src/main/java/com/vaultguard/service/AdminService.java`
- Modify: `springboot/src/main/java/com/vaultguard/api/admin/AdminController.java`
- Modify: `springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java`

- [ ] **Step 1: Add failing tests for pagination, sort, filter, and X-Total-Count on listUsers**

Open `springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java`. Add these imports if missing:

```java
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import com.vaultguard.util.UuidUtil;
```

In `setUp()`, append four more users so pagination tests have something to slice. Replace the existing `setUp()` body with:

```java
    @BeforeEach
    void setUp() {
        deviceRepository.deleteAll();
        userRepository.deleteAll();
        String[] emails = {
            "alpha@example.com",
            "bravo@example.com",
            "charlie@example.com",
            "delta@example.com",
            "echo@example.com"
        };
        for (String e : emails) {
            User u = new User();
            u.setUuid(UuidUtil.newUuid());
            u.setEmail(e);
            u.setName("User " + e.split("@")[0]);
            u.setPasswordHash(passwordHashService.hashForStorage("hash"));
            u.setSecurityStamp(UuidUtil.newUuid());
            userRepository.save(u);
        }
    }
```

Replace the existing `adminUsersReturnUserDetails` test's assertions to match a known email instead of `admin-test@example.com`:

```java
    @Test
    void adminUsersReturnUserDetails() throws Exception {
        mockMvc.perform(get("/api/admin/users")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").isNotEmpty())
            .andExpect(jsonPath("$[0].cipherCount").exists());
    }
```

Also update `adminDeleteUserRemovesUser` to expect 4 remaining (instead of 0):

```java
    @Test
    void adminDeleteUserRemovesUser() throws Exception {
        String uuid = userRepository.findAll().get(0).getUuid();

        mockMvc.perform(delete("/api/admin/users/" + uuid)
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/users")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4));
    }
```

Then add the new tests at the bottom of the class (before the closing brace):

```java
    @Test
    void adminUsersPaginationSlices() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=2&sort=email,asc")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("alpha@example.com"))
            .andExpect(jsonPath("$[1].email").value("bravo@example.com"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("X-Total-Count", "5"));
    }

    @Test
    void adminUsersSecondPage() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=1&size=2&sort=email,asc")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("charlie@example.com"));
    }

    @Test
    void adminUsersFilterByEmail() throws Exception {
        mockMvc.perform(get("/api/admin/users?q=alpha")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value("alpha@example.com"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("X-Total-Count", "1"));
    }

    @Test
    void adminUsersSortDesc() throws Exception {
        mockMvc.perform(get("/api/admin/users?sort=email,desc")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("echo@example.com"));
    }

    @Test
    void adminUsersUnknownSortFieldIgnored() throws Exception {
        mockMvc.perform(get("/api/admin/users?sort=notAField,asc")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void adminUsersSizeCappedAt100() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=10000")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void adminUsersXTotalCountWithoutPagination() throws Exception {
        mockMvc.perform(get("/api/admin/users")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("X-Total-Count", "5"));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test -Dtest=AdminControllerTest 2>&1 | tail -25
```

Expected: the new pagination/sort/filter tests fail (404 or wrong shape). `adminUsersPaginationSlices` should fail because the controller ignores query params and returns all 5 users.

- [ ] **Step 3: Create the `AdminPage` record**

Create `springboot/src/main/java/com/vaultguard/service/AdminPage.java`:

```java
package com.vaultguard.service;

import java.util.List;

/** Result of an admin list query: a page slice plus the total count of unfiltered/filtered rows. */
public record AdminPage<T>(List<T> data, long total) {}
```

- [ ] **Step 4: Add a paginated `listUsers` overload to `AdminService`**

Open `springboot/src/main/java/com/vaultguard/service/AdminService.java`. Add these imports:

```java
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.vaultguard.db.entity.User;
```

(`User` may already be imported via `entity.*` star import — check and add only if absent.)

Replace the existing `listUsers()` method (around line 49) with two methods — keep the no-arg version for backward compatibility with other callers, and add the paginated one:

```java
    public List<Map<String, Object>> listUsers() {
        return listUsers(0, Integer.MAX_VALUE, null, null).data();
    }

    private static final java.util.Set<String> USER_SORT_FIELDS =
        java.util.Set.of("email", "name", "createdAt", "enabled");

    public AdminPage<Map<String, Object>> listUsers(int page, int size, String sort, String q) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Sort springSort = parseSort(sort, USER_SORT_FIELDS);
        PageRequest pageable = PageRequest.of(safePage, safeSize, springSort);

        org.springframework.data.domain.Page<User> users;
        if (q != null && !q.isBlank()) {
            users = userRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(q, q, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        List<Map<String, Object>> rows = users.getContent().stream().map(this::toUserSummary).toList();
        return new AdminPage<>(rows, users.getTotalElements());
    }

    private static Sort parseSort(String sortParam, java.util.Set<String> allowed) {
        if (sortParam == null || sortParam.isBlank()) return Sort.unsorted();
        String[] parts = sortParam.split(",", 2);
        String field = parts[0].trim();
        if (!allowed.contains(field)) return Sort.unsorted();
        Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }
```

- [ ] **Step 5: Add the filter query method to `UserRepository`**

Open `springboot/src/main/java/com/vaultguard/db/repository/UserRepository.java` and add this Spring Data derived query method to the interface:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// ... inside the interface:
Page<com.vaultguard.db.entity.User> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(
    String email, String name, Pageable pageable);
```

(If `Page`, `Pageable`, or `User` are already imported, don't duplicate.)

- [ ] **Step 6: Wire pagination into `AdminController.listUsers`**

Open `springboot/src/main/java/com/vaultguard/api/admin/AdminController.java`. Replace the existing `listUsers()` method (lines 27-30) with:

```java
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size,
        @RequestParam(name = "sort", required = false) String sort,
        @RequestParam(name = "q", required = false) String q) {
        AdminPage<Map<String, Object>> result = adminService.listUsers(page, size, sort, q);
        return ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(result.total()))
            .body(result.data());
    }
```

Add the import at the top if not already present:

```java
import com.vaultguard.service.AdminPage;
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test -Dtest=AdminControllerTest 2>&1 | tail -15
```

Expected: all `AdminControllerTest` tests pass.

- [ ] **Step 8: Run the full test suite (no regressions)**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test 2>&1 | grep -E "Tests run:|BUILD|ERROR" | tail -10
```

Expected: `BUILD SUCCESS`, no failing tests.

- [ ] **Step 9: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add springboot/src/main/java/com/vaultguard/service/AdminPage.java springboot/src/main/java/com/vaultguard/service/AdminService.java springboot/src/main/java/com/vaultguard/db/repository/UserRepository.java springboot/src/main/java/com/vaultguard/api/admin/AdminController.java springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java && git commit -m "feat: paginate /api/admin/users with sort, filter, X-Total-Count"
```

---

## Task 3: Backend — Pagination/Sort/Filter on `/api/admin/organizations` + `X-Total-Count`

**Files:**
- Modify: `springboot/src/main/java/com/vaultguard/service/AdminService.java`
- Modify: `springboot/src/main/java/com/vaultguard/db/repository/OrganizationRepository.java`
- Modify: `springboot/src/main/java/com/vaultguard/api/admin/AdminController.java`
- Modify: `springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java`

- [ ] **Step 1: Add failing tests for orgs pagination, sort, filter, and X-Total-Count**

Open `springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java`. Inject `OrganizationRepository` at the top with the other `@Autowired` fields:

```java
@Autowired com.vaultguard.db.repository.OrganizationRepository organizationRepository;
```

Extend `setUp()` to seed three orgs after the user seeding:

```java
        organizationRepository.deleteAll();
        String[] orgNames = {"Acme Inc", "Bravo LLC", "Charlie Corp"};
        for (String n : orgNames) {
            com.vaultguard.db.entity.Organization org = new com.vaultguard.db.entity.Organization();
            org.setUuid(UuidUtil.newUuid());
            org.setName(n);
            org.setBillingEmail(n.split(" ")[0].toLowerCase() + "@example.com");
            organizationRepository.save(org);
        }
```

Add these test methods at the bottom of the class:

```java
    @Test
    void adminOrgsList() throws Exception {
        mockMvc.perform(get("/api/admin/organizations")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("X-Total-Count", "3"));
    }

    @Test
    void adminOrgsPaginationSlices() throws Exception {
        mockMvc.perform(get("/api/admin/organizations?page=0&size=2&sort=name,asc")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Acme Inc"))
            .andExpect(jsonPath("$[1].name").value("Bravo LLC"));
    }

    @Test
    void adminOrgsFilterByName() throws Exception {
        mockMvc.perform(get("/api/admin/organizations?q=acme")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Acme Inc"));
    }

    @Test
    void adminOrgsSortDesc() throws Exception {
        mockMvc.perform(get("/api/admin/organizations?sort=name,desc")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Charlie Corp"));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test -Dtest=AdminControllerTest 2>&1 | tail -20
```

Expected: the new org tests fail because the controller ignores query params.

- [ ] **Step 3: Add paginated `listOrganizations` to `AdminService`**

Open `springboot/src/main/java/com/vaultguard/service/AdminService.java`. Replace the existing `listOrganizations()` method (around line 108) with:

```java
    public List<Map<String, Object>> listOrganizations() {
        return listOrganizations(0, Integer.MAX_VALUE, null, null).data();
    }

    private static final java.util.Set<String> ORG_SORT_FIELDS =
        java.util.Set.of("name", "billingEmail");

    public AdminPage<Map<String, Object>> listOrganizations(int page, int size, String sort, String q) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Sort springSort = parseSort(sort, ORG_SORT_FIELDS);
        PageRequest pageable = PageRequest.of(safePage, safeSize, springSort);

        org.springframework.data.domain.Page<com.vaultguard.db.entity.Organization> orgs;
        if (q != null && !q.isBlank()) {
            orgs = organizationRepository.findByNameContainingIgnoreCaseOrBillingEmailContainingIgnoreCase(q, q, pageable);
        } else {
            orgs = organizationRepository.findAll(pageable);
        }
        List<Map<String, Object>> rows = orgs.getContent().stream().map(org -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", org.getUuid());
            m.put("name", org.getName());
            m.put("billingEmail", org.getBillingEmail());
            m.put("userCount", orgMembershipRepository.countByOrgUuid(org.getUuid()));
            m.put("cipherCount", cipherRepository.countByOrganizationUuid(org.getUuid()));
            m.put("collectionCount", collectionRepository.countByOrgUuid(org.getUuid()));
            return m;
        }).toList();
        return new AdminPage<>(rows, orgs.getTotalElements());
    }
```

- [ ] **Step 4: Add the org filter query method to `OrganizationRepository`**

Open `springboot/src/main/java/com/vaultguard/db/repository/OrganizationRepository.java` and add:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// inside the interface:
Page<com.vaultguard.db.entity.Organization> findByNameContainingIgnoreCaseOrBillingEmailContainingIgnoreCase(
    String name, String email, Pageable pageable);
```

- [ ] **Step 5: Wire pagination into `AdminController.listOrganizations`**

Open `springboot/src/main/java/com/vaultguard/api/admin/AdminController.java`. Replace the existing `listOrganizations()` (lines 74-77) with:

```java
    @GetMapping("/organizations")
    public ResponseEntity<List<Map<String, Object>>> listOrganizations(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size,
        @RequestParam(name = "sort", required = false) String sort,
        @RequestParam(name = "q", required = false) String q) {
        AdminPage<Map<String, Object>> result = adminService.listOrganizations(page, size, sort, q);
        return ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(result.total()))
            .body(result.data());
    }
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test -Dtest=AdminControllerTest 2>&1 | tail -15
```

Expected: all `AdminControllerTest` tests pass.

- [ ] **Step 7: Full suite**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add springboot/src/main/java/com/vaultguard/service/AdminService.java springboot/src/main/java/com/vaultguard/db/repository/OrganizationRepository.java springboot/src/main/java/com/vaultguard/api/admin/AdminController.java springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java && git commit -m "feat: paginate /api/admin/organizations with sort, filter, X-Total-Count"
```

---

## Task 4: Backend — CORS on `/api/admin/**`

**Files:**
- Modify: `springboot/src/main/java/com/vaultguard/config/SecurityConfig.java`
- Modify: `springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java`

- [ ] **Step 1: Add failing CORS tests**

Open `springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java`. Add these tests at the bottom of the class:

```java
    @Test
    void corsPreflightAllowedForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/admin/users")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "X-Admin-Token"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Access-Control-Expose-Headers",
                    org.hamcrest.Matchers.containsString("X-Total-Count")));
    }

    @Test
    void corsPreflightRejectedForNonAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/admin/users")
            .header("Origin", "http://evil.example.com")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "X-Admin-Token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void corsActualGetIncludesAllowOriginHeader() throws Exception {
        mockMvc.perform(get("/api/admin/users")
            .header("Origin", "http://localhost:5173")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test -Dtest=AdminControllerTest 2>&1 | tail -20
```

Expected: the three CORS tests fail (preflight returns 401 from AdminAuthFilter, or no CORS headers present).

- [ ] **Step 3: Wire CORS in `SecurityConfig`**

Open `springboot/src/main/java/com/vaultguard/config/SecurityConfig.java`. Add these imports:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
```

Add the `VaultGuardProperties` dependency to the constructor. Replace the constructor and field block (lines 18-26) with:

```java
    private final JwtService jwtService;
    private final RateLimitFilter rateLimitFilter;
    private final AdminAuthFilter adminAuthFilter;
    private final VaultGuardProperties props;

    public SecurityConfig(JwtService jwtService,
                          RateLimitFilter rateLimitFilter,
                          AdminAuthFilter adminAuthFilter,
                          VaultGuardProperties props) {
        this.jwtService = jwtService;
        this.rateLimitFilter = rateLimitFilter;
        this.adminAuthFilter = adminAuthFilter;
        this.props = props;
    }
```

In `securityFilterChain`, enable CORS by inserting `.cors(c -> c.configurationSource(adminCorsConfigurationSource()))` between `.csrf(...)` and `.sessionManagement(...)`. The block becomes:

```java
        http
            .csrf(csrf -> csrf.disable())
            .cors(c -> c.configurationSource(adminCorsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // ... rest unchanged
```

Add a new bean method before the closing brace of the class:

```java
    @Bean
    public CorsConfigurationSource adminCorsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = props.getAdminCorsOrigins();
        if (origins != null && !origins.isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(origins);
            cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of("X-Admin-Token", "Content-Type"));
            cors.setExposedHeaders(List.of("X-Total-Count"));
            cors.setAllowCredentials(false);
            cors.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/admin/**", cors);
        }
        return source;
    }
```

- [ ] **Step 4: Make `AdminAuthFilter` skip preflight `OPTIONS` requests**

Open `springboot/src/main/java/com/vaultguard/config/AdminAuthFilter.java` and locate the `doFilterInternal` method (or the equivalent filter logic). Add an early return at the top of the request-processing block, before the token check:

```java
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
```

This must be inserted before any 401-emitting code. If the filter currently extends `OncePerRequestFilter`, the existing method signature already provides `filterChain`. If the file structure differs, place the OPTIONS short-circuit at the very top of the filter logic. Then run the tests below.

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test -Dtest=AdminControllerTest 2>&1 | tail -15
```

Expected: all `AdminControllerTest` tests pass, including the three CORS tests.

- [ ] **Step 6: Full suite**

```bash
cd /Users/ho/workspace/github/vaultguard/springboot && mvn -q test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add springboot/src/main/java/com/vaultguard/config/SecurityConfig.java springboot/src/main/java/com/vaultguard/config/AdminAuthFilter.java springboot/src/test/java/com/vaultguard/api/admin/AdminControllerTest.java && git commit -m "feat: enable CORS for /api/admin/** with configurable origin allow-list"
```

---

## Task 5: Frontend — Vite + React + TypeScript scaffold

**Files:**
- Create: `admin-ui/package.json`
- Create: `admin-ui/vite.config.ts`
- Create: `admin-ui/vitest.config.ts`
- Create: `admin-ui/tsconfig.json`
- Create: `admin-ui/tsconfig.node.json`
- Create: `admin-ui/index.html`
- Create: `admin-ui/src/main.tsx`
- Create: `admin-ui/src/App.tsx`
- Create: `admin-ui/.gitignore`

- [ ] **Step 1: Create `admin-ui/package.json`**

```json
{
  "name": "vaultguard-admin-ui",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@mui/icons-material": "^6.2.0",
    "@mui/material": "^6.2.0",
    "@tanstack/react-query": "^5.62.7",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^7.1.1",
    "react-admin": "^5.4.0"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.1.0",
    "@testing-library/user-event": "^14.5.2",
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.4",
    "jsdom": "^25.0.1",
    "msw": "^2.7.0",
    "typescript": "^5.6.3",
    "vite": "^5.4.11",
    "vitest": "^2.1.8"
  }
}
```

- [ ] **Step 2: Create `admin-ui/vite.config.ts`**

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api/admin": {
        target: "http://localhost:8080",
        changeOrigin: false,
      },
    },
  },
});
```

- [ ] **Step 3: Create `admin-ui/vitest.config.ts`**

```ts
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.ts"],
    include: ["tests/**/*.test.{ts,tsx}"],
  },
});
```

- [ ] **Step 4: Create `admin-ui/tests/setup.ts`**

```ts
import "@testing-library/jest-dom/vitest";
```

- [ ] **Step 5: Create `admin-ui/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": false,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src", "tests"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 6: Create `admin-ui/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts", "vitest.config.ts"]
}
```

- [ ] **Step 7: Create `admin-ui/index.html`**

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>VaultGuard Admin</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 8: Create `admin-ui/src/main.tsx`**

```tsx
import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

- [ ] **Step 9: Create stub `admin-ui/src/App.tsx`** (replaced in Task 13)

```tsx
import { Admin, Resource, ListGuesser } from "react-admin";

export function App() {
  return (
    <Admin>
      <Resource name="users" list={ListGuesser} />
    </Admin>
  );
}
```

- [ ] **Step 10: Create `admin-ui/.gitignore`**

```
node_modules/
dist/
.vite/
.env.local
*.log
.DS_Store
```

- [ ] **Step 11: Install dependencies and verify build works**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npm install 2>&1 | tail -5
```

Expected: install completes with `added N packages`.

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx tsc -b 2>&1 | tail -10
```

Expected: no errors (TypeScript compilation passes).

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run 2>&1 | tail -10
```

Expected: `No test files found, exiting with code 0` or similar — vitest invocation works.

- [ ] **Step 12: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/ && git commit -m "feat: scaffold admin-ui Vite + React + TS project"
```

Note: `node_modules/` is ignored. `package-lock.json` should be committed.

---

## Task 6: Frontend — `types.ts` + `httpClient.ts` + httpClient tests

**Files:**
- Create: `admin-ui/src/types.ts`
- Create: `admin-ui/src/httpClient.ts`
- Create: `admin-ui/tests/httpClient.test.ts`

- [ ] **Step 1: Write failing `httpClient` tests**

Create `admin-ui/tests/httpClient.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { httpClient, HttpError, TOKEN_KEY } from "../src/httpClient";

describe("httpClient", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it("attaches X-Admin-Token header when token is in sessionStorage", async () => {
    sessionStorage.setItem(TOKEN_KEY, "secret-token");
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await httpClient("/api/admin/users");

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("X-Admin-Token")).toBe("secret-token");
  });

  it("omits X-Admin-Token header when no token is stored", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    );

    await httpClient("/api/admin/users");

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("X-Admin-Token")).toBeNull();
  });

  it("parses JSON body for 2xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ a: 1 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await httpClient("/api/admin/diagnostics");
    expect(result.json).toEqual({ a: 1 });
    expect(result.status).toBe(200);
  });

  it("exposes response headers", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("[]", {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Total-Count": "42" },
      })
    );

    const result = await httpClient("/api/admin/users");
    expect(result.headers.get("X-Total-Count")).toBe("42");
  });

  it("throws HttpError with status and body for non-2xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("not found", { status: 404 })
    );

    await expect(httpClient("/api/admin/users/missing")).rejects.toMatchObject({
      status: 404,
    });
  });

  it("wraps network rejection as HttpError(0, 'Network error')", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(new TypeError("fetch failed"));

    await expect(httpClient("/api/admin/users")).rejects.toMatchObject({
      status: 0,
      message: "Network error",
    });
  });

  it("HttpError is an instance of Error", () => {
    const e = new HttpError(401, "Unauthorized");
    expect(e).toBeInstanceOf(Error);
    expect(e.status).toBe(401);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run 2>&1 | tail -20
```

Expected: tests fail because `../src/httpClient` doesn't exist.

- [ ] **Step 3: Create `admin-ui/src/types.ts`**

```ts
export const TOKEN_KEY = "vaultguardAdminToken";

export interface UserRow {
  id: string;
  name: string | null;
  email: string;
  enabled: boolean;
  emailVerified: boolean;
  twoFactorEnabled: boolean;
  createdAt: string;
  cipherCount: number;
  attachmentCount: number;
  organizations: { id: string; name: string }[];
}

export interface OrgRow {
  id: string;
  name: string;
  billingEmail: string | null;
  userCount: number;
  cipherCount: number;
  collectionCount: number;
}

export interface AdminSettings {
  domain: string;
  signupsAllowed: boolean;
  invitationsAllowed: boolean;
  passwordIterations: number;
  mail: { from: string; fromName: string };
}

export interface Diagnostics {
  version: string;
  javaVersion: string;
  javaVendor: string;
  osName: string;
  osArch: string;
  serverTime: string;
  domain: string;
}
```

- [ ] **Step 4: Create `admin-ui/src/httpClient.ts`**

```ts
import { TOKEN_KEY } from "./types";

export { TOKEN_KEY };

export class HttpError extends Error {
  status: number;
  body: unknown;
  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.name = "HttpError";
    this.status = status;
    this.body = body;
  }
}

export interface HttpResponse<T> {
  status: number;
  headers: Headers;
  json: T;
}

export async function httpClient<T = unknown>(
  path: string,
  init: RequestInit = {}
): Promise<HttpResponse<T>> {
  const headers = new Headers(init.headers);
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) headers.set("X-Admin-Token", token);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  let response: Response;
  try {
    response = await fetch(path, { ...init, headers });
  } catch {
    throw new HttpError(0, "Network error");
  }
  const text = await response.text();
  let parsed: unknown = undefined;
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }
  if (!response.ok) {
    const message =
      typeof parsed === "object" && parsed && "message" in parsed
        ? String((parsed as Record<string, unknown>).message)
        : response.statusText || `HTTP ${response.status}`;
    throw new HttpError(response.status, message, parsed);
  }
  return { status: response.status, headers: response.headers, json: parsed as T };
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/httpClient.test.ts 2>&1 | tail -15
```

Expected: all 7 httpClient tests pass.

- [ ] **Step 6: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/src/types.ts admin-ui/src/httpClient.ts admin-ui/tests/httpClient.test.ts && git commit -m "feat: add typed httpClient with X-Admin-Token injection and HttpError"
```

---

## Task 7: Frontend — `dataProvider.ts` CRUD methods + tests

**Files:**
- Create: `admin-ui/src/dataProvider.ts`
- Create: `admin-ui/tests/dataProvider.test.ts`

- [ ] **Step 1: Write failing `dataProvider` CRUD tests**

Create `admin-ui/tests/dataProvider.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from "vitest";
import { dataProvider } from "../src/dataProvider";

function mockFetchOk(json: unknown, headers: Record<string, string> = {}) {
  return vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
    new Response(JSON.stringify(json), {
      status: 200,
      headers: { "Content-Type": "application/json", ...headers },
    })
  );
}

describe("dataProvider CRUD", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("getList('users') sends page, size, sort, q query params", async () => {
    const fetchSpy = mockFetchOk([], { "X-Total-Count": "0" });

    await dataProvider.getList("users", {
      pagination: { page: 2, perPage: 25 },
      sort: { field: "email", order: "ASC" },
      filter: { q: "alice" },
    });

    const url = fetchSpy.mock.calls[0][0] as string;
    expect(url).toContain("/api/admin/users?");
    expect(url).toContain("page=1"); // RA pagination.page is 1-based; backend is 0-based
    expect(url).toContain("size=25");
    expect(url).toContain("sort=email%2Casc");
    expect(url).toContain("q=alice");
  });

  it("getList('users') returns { data, total } from X-Total-Count", async () => {
    mockFetchOk([{ id: "u1", email: "a@b.com" }], { "X-Total-Count": "100" });

    const result = await dataProvider.getList("users", {
      pagination: { page: 1, perPage: 25 },
      sort: { field: "email", order: "ASC" },
      filter: {},
    });

    expect(result).toEqual({ data: [{ id: "u1", email: "a@b.com" }], total: 100 });
  });

  it("getList falls back to data.length when X-Total-Count missing", async () => {
    mockFetchOk([{ id: "u1" }, { id: "u2" }]);

    const result = await dataProvider.getList("users", {
      pagination: { page: 1, perPage: 25 },
      sort: { field: "id", order: "ASC" },
      filter: {},
    });

    expect(result.total).toBe(2);
  });

  it("getList('organizations') uses the organizations URL", async () => {
    const fetchSpy = mockFetchOk([], { "X-Total-Count": "0" });

    await dataProvider.getList("organizations", {
      pagination: { page: 1, perPage: 25 },
      sort: { field: "name", order: "ASC" },
      filter: {},
    });

    expect(fetchSpy.mock.calls[0][0]).toContain("/api/admin/organizations?");
  });

  it("delete('users') hits DELETE /api/admin/users/{id}", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    const result = await dataProvider.delete("users", { id: "abc-123", previousData: { id: "abc-123" } });

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/abc-123");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("DELETE");
    expect(result).toEqual({ data: { id: "abc-123" } });
  });

  it("delete('organizations') hits DELETE /api/admin/organizations/{id}", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    await dataProvider.delete("organizations", { id: "org-1", previousData: { id: "org-1" } });

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/organizations/org-1");
  });

  it("deleteMany('users') deletes each id in sequence", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    const result = await dataProvider.deleteMany("users", { ids: ["u1", "u2", "u3"] });

    expect(fetchSpy).toHaveBeenCalledTimes(3);
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1");
    expect(fetchSpy.mock.calls[1][0]).toBe("/api/admin/users/u2");
    expect(fetchSpy.mock.calls[2][0]).toBe("/api/admin/users/u3");
    expect(result).toEqual({ data: ["u1", "u2", "u3"] });
  });

  it("getOne throws NotImplemented", async () => {
    await expect(dataProvider.getOne("users", { id: "u1" })).rejects.toThrow(/not supported/i);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/dataProvider.test.ts 2>&1 | tail -10
```

Expected: tests fail (module doesn't export `dataProvider`).

- [ ] **Step 3: Create `admin-ui/src/dataProvider.ts` with CRUD methods**

```ts
import type { DataProvider } from "react-admin";
import { httpClient } from "./httpClient";

const RESOURCE_URL: Record<string, string> = {
  users: "/api/admin/users",
  organizations: "/api/admin/organizations",
};

function urlFor(resource: string): string {
  const u = RESOURCE_URL[resource];
  if (!u) throw new Error(`Unknown resource: ${resource}`);
  return u;
}

export const dataProvider: DataProvider = {
  async getList(resource, params) {
    const { page, perPage } = params.pagination ?? { page: 1, perPage: 25 };
    const sort = params.sort;
    const filter = (params.filter ?? {}) as Record<string, unknown>;
    const search = new URLSearchParams();
    search.set("page", String(page - 1)); // RA is 1-based, backend is 0-based
    search.set("size", String(perPage));
    if (sort && sort.field) {
      search.set("sort", `${sort.field},${sort.order.toLowerCase()}`);
    }
    if (typeof filter.q === "string" && filter.q.length > 0) {
      search.set("q", filter.q);
    }
    const response = await httpClient<unknown[]>(`${urlFor(resource)}?${search}`);
    const total = response.headers.get("X-Total-Count");
    const data = response.json as { id: string | number }[];
    return { data: data as any, total: total !== null ? parseInt(total, 10) : data.length };
  },

  async getOne(_resource, _params) {
    throw new Error("getOne not supported by the admin API");
  },

  async getMany(_resource, _params) {
    throw new Error("getMany not supported by the admin API");
  },

  async getManyReference(_resource, _params) {
    throw new Error("getManyReference not supported by the admin API");
  },

  async create(_resource, _params) {
    throw new Error("create not supported by the admin API");
  },

  async update(_resource, _params) {
    throw new Error("update not supported by the admin API");
  },

  async updateMany(_resource, _params) {
    throw new Error("updateMany not supported by the admin API");
  },

  async delete(resource, params) {
    await httpClient(`${urlFor(resource)}/${params.id}`, { method: "DELETE" });
    return { data: (params.previousData ?? { id: params.id }) as any };
  },

  async deleteMany(resource, params) {
    for (const id of params.ids) {
      await httpClient(`${urlFor(resource)}/${id}`, { method: "DELETE" });
    }
    return { data: params.ids as any };
  },
};
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/dataProvider.test.ts 2>&1 | tail -15
```

Expected: all 8 CRUD tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/src/dataProvider.ts admin-ui/tests/dataProvider.test.ts && git commit -m "feat: add dataProvider with getList + delete for users and organizations"
```

---

## Task 8: Frontend — Custom admin action methods on dataProvider

**Files:**
- Modify: `admin-ui/src/dataProvider.ts`
- Modify: `admin-ui/tests/dataProvider.test.ts`

- [ ] **Step 1: Add failing tests for custom action methods**

Append to `admin-ui/tests/dataProvider.test.ts` (after the existing `describe`):

```ts
import { adminActions } from "../src/dataProvider";

describe("dataProvider admin actions", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("disableUser posts to /users/{id}/disable", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ id: "u1", enabled: false }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await adminActions.disableUser("u1");

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1/disable");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("POST");
    expect(result).toEqual({ id: "u1", enabled: false });
  });

  it("enableUser posts to /users/{id}/enable", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ id: "u1", enabled: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await adminActions.enableUser("u1");

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1/enable");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("POST");
  });

  it("deauthUser posts to /users/{id}/deauth", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    await adminActions.deauthUser("u1");

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1/deauth");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("POST");
  });

  it("removeTwoFactor deletes /users/{id}/2fa", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    await adminActions.removeTwoFactor("u1");

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1/2fa");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("DELETE");
  });

  it("getSettings GETs /api/admin/settings", async () => {
    const settings = {
      domain: "http://localhost:8080",
      signupsAllowed: true,
      invitationsAllowed: true,
      passwordIterations: 600000,
      mail: { from: "x", fromName: "y" },
    };
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify(settings), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await adminActions.getSettings();

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/settings");
    expect(result).toEqual(settings);
  });

  it("saveSettings POSTs JSON to /api/admin/settings", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await adminActions.saveSettings({ signupsAllowed: false } as any);

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/settings");
    expect(init.method).toBe("POST");
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
    expect(init.body).toBe(JSON.stringify({ signupsAllowed: false }));
  });

  it("getDiagnostics GETs /api/admin/diagnostics", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ version: "1.0.0" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await adminActions.getDiagnostics();

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/diagnostics");
    expect(result).toEqual({ version: "1.0.0" });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/dataProvider.test.ts 2>&1 | tail -10
```

Expected: `adminActions` import fails — tests do not run.

- [ ] **Step 3: Add `adminActions` to `dataProvider.ts`**

Open `admin-ui/src/dataProvider.ts`. Add these imports at the top:

```ts
import type { AdminSettings, Diagnostics } from "./types";
```

Append after the `dataProvider` export:

```ts
export const adminActions = {
  async disableUser(id: string): Promise<{ id: string; enabled: boolean }> {
    const r = await httpClient<{ id: string; enabled: boolean }>(
      `/api/admin/users/${id}/disable`,
      { method: "POST" }
    );
    return r.json;
  },

  async enableUser(id: string): Promise<{ id: string; enabled: boolean }> {
    const r = await httpClient<{ id: string; enabled: boolean }>(
      `/api/admin/users/${id}/enable`,
      { method: "POST" }
    );
    return r.json;
  },

  async deauthUser(id: string): Promise<void> {
    await httpClient(`/api/admin/users/${id}/deauth`, { method: "POST" });
  },

  async removeTwoFactor(id: string): Promise<void> {
    await httpClient(`/api/admin/users/${id}/2fa`, { method: "DELETE" });
  },

  async getSettings(): Promise<AdminSettings> {
    const r = await httpClient<AdminSettings>("/api/admin/settings");
    return r.json;
  },

  async saveSettings(body: Partial<AdminSettings>): Promise<AdminSettings> {
    const r = await httpClient<AdminSettings>("/api/admin/settings", {
      method: "POST",
      body: JSON.stringify(body),
    });
    return r.json;
  },

  async getDiagnostics(): Promise<Diagnostics> {
    const r = await httpClient<Diagnostics>("/api/admin/diagnostics");
    return r.json;
  },
};
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/dataProvider.test.ts 2>&1 | tail -15
```

Expected: all CRUD + admin-actions tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/src/dataProvider.ts admin-ui/tests/dataProvider.test.ts && git commit -m "feat: add adminActions for disable/enable/deauth/2fa/settings/diagnostics"
```

---

## Task 9: Frontend — `authProvider.ts` + tests

**Files:**
- Create: `admin-ui/src/authProvider.ts`
- Create: `admin-ui/tests/authProvider.test.ts`

- [ ] **Step 1: Write failing `authProvider` tests**

Create `admin-ui/tests/authProvider.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from "vitest";
import { authProvider } from "../src/authProvider";
import { TOKEN_KEY } from "../src/types";

describe("authProvider", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("login stores token on 200 diagnostics response", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ version: "1.0.0" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await authProvider.login({ token: "valid-token" });

    expect(sessionStorage.getItem(TOKEN_KEY)).toBe("valid-token");
  });

  it("login sends X-Admin-Token header to diagnostics", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    );

    await authProvider.login({ token: "abc" });

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("X-Admin-Token")).toBe("abc");
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/diagnostics");
  });

  it("login rejects and does not store on non-2xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("unauthorized", { status: 401 })
    );

    await expect(authProvider.login({ token: "bad" })).rejects.toThrow(/invalid/i);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("login rejects on network failure", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(new TypeError("fetch failed"));

    await expect(authProvider.login({ token: "x" })).rejects.toThrow();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("checkAuth resolves when token present", async () => {
    sessionStorage.setItem(TOKEN_KEY, "stored");
    await expect(authProvider.checkAuth({})).resolves.toBeUndefined();
  });

  it("checkAuth rejects when token absent", async () => {
    await expect(authProvider.checkAuth({})).rejects.toBeDefined();
  });

  it("logout clears storage", async () => {
    sessionStorage.setItem(TOKEN_KEY, "x");
    await authProvider.logout({});
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("checkError rejects on 401", async () => {
    await expect(authProvider.checkError({ status: 401 } as any)).rejects.toBeDefined();
  });

  it("checkError rejects on 403", async () => {
    await expect(authProvider.checkError({ status: 403 } as any)).rejects.toBeDefined();
  });

  it("checkError resolves on 500", async () => {
    await expect(authProvider.checkError({ status: 500 } as any)).resolves.toBeUndefined();
  });

  it("getIdentity returns admin identity", async () => {
    const id = await authProvider.getIdentity!();
    expect(id).toMatchObject({ id: "admin" });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/authProvider.test.ts 2>&1 | tail -10
```

Expected: module not found.

- [ ] **Step 3: Create `admin-ui/src/authProvider.ts`**

```ts
import type { AuthProvider } from "react-admin";
import { TOKEN_KEY } from "./types";

interface LoginParams {
  token: string;
}

export const authProvider: AuthProvider = {
  async login(params: LoginParams | unknown) {
    const token = (params as LoginParams).token;
    if (!token) throw new Error("Token required");
    let response: Response;
    try {
      response = await fetch("/api/admin/diagnostics", {
        method: "GET",
        headers: { "X-Admin-Token": token },
      });
    } catch {
      throw new Error("Network error");
    }
    if (!response.ok) {
      throw new Error("Invalid admin token");
    }
    sessionStorage.setItem(TOKEN_KEY, token);
  },

  async checkAuth() {
    if (!sessionStorage.getItem(TOKEN_KEY)) {
      throw new Error("Not authenticated");
    }
  },

  async checkError(error: { status?: number }) {
    if (error?.status === 401 || error?.status === 403) {
      sessionStorage.removeItem(TOKEN_KEY);
      throw new Error("Not authenticated");
    }
  },

  async logout() {
    sessionStorage.removeItem(TOKEN_KEY);
  },

  async getIdentity() {
    return { id: "admin", fullName: "Admin" };
  },

  async getPermissions() {
    return undefined;
  },
};
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/authProvider.test.ts 2>&1 | tail -15
```

Expected: all 11 authProvider tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/src/authProvider.ts admin-ui/tests/authProvider.test.ts && git commit -m "feat: add authProvider with X-Admin-Token + sessionStorage"
```

---

## Task 10: Frontend — `LoginPage.tsx` + component test

**Files:**
- Create: `admin-ui/src/pages/LoginPage.tsx`
- Create: `admin-ui/tests/LoginPage.test.tsx`

- [ ] **Step 1: Write failing component test**

Create `admin-ui/tests/LoginPage.test.tsx`:

```tsx
import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthContext, NotificationContextProvider } from "react-admin";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "../src/pages/LoginPage";
import { authProvider } from "../src/authProvider";
import { TOKEN_KEY } from "../src/types";

function renderLogin() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={authProvider}>
        <NotificationContextProvider>
          <MemoryRouter>
            <LoginPage />
          </MemoryRouter>
        </NotificationContextProvider>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("renders a token input and a submit button", () => {
    renderLogin();
    expect(screen.getByLabelText(/admin token/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("submits the token and stores it on success", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    );

    renderLogin();

    await userEvent.type(screen.getByLabelText(/admin token/i), "good-token");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    // Wait one tick for the async login
    await new Promise((r) => setTimeout(r, 50));
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe("good-token");
  });

  it("shows an error message on invalid token", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("nope", { status: 401 })
    );

    renderLogin();

    await userEvent.type(screen.getByLabelText(/admin token/i), "bad-token");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText(/invalid admin token/i)).toBeInTheDocument();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
```

(`@tanstack/react-query` is already in `dependencies` from Task 5 — it's react-admin v5's peer dep and the test needs it to render the `<QueryClientProvider>`.)

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/LoginPage.test.tsx 2>&1 | tail -10
```

Expected: module `../src/pages/LoginPage` not found.

- [ ] **Step 3: Create `admin-ui/src/pages/LoginPage.tsx`**

```tsx
import { useState, FormEvent } from "react";
import { useLogin, useNotify } from "react-admin";

export function LoginPage() {
  const [token, setToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const login = useLogin();
  const notify = useNotify();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await login({ token });
    } catch (err) {
      const msg = (err as Error).message || "Invalid admin token";
      setError(msg);
      notify(msg, { type: "error" });
    }
  }

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "100vh",
        background: "#f5f5f5",
      }}
    >
      <form
        onSubmit={onSubmit}
        style={{
          background: "white",
          padding: "2rem",
          borderRadius: "8px",
          boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
          width: "320px",
        }}
      >
        <h1 style={{ marginTop: 0, fontSize: "1.25rem" }}>VaultGuard Admin</h1>
        <label htmlFor="admin-token" style={{ display: "block", marginBottom: "0.5rem" }}>
          Admin token
        </label>
        <input
          id="admin-token"
          type="password"
          value={token}
          onChange={(e) => setToken(e.target.value)}
          autoFocus
          required
          style={{
            width: "100%",
            padding: "0.5rem",
            marginBottom: "1rem",
            border: "1px solid #ccc",
            borderRadius: "4px",
            boxSizing: "border-box",
          }}
        />
        {error && (
          <div role="alert" style={{ color: "#b00020", marginBottom: "1rem" }}>
            {error}
          </div>
        )}
        <button
          type="submit"
          style={{
            width: "100%",
            padding: "0.5rem",
            background: "#1976d2",
            color: "white",
            border: "none",
            borderRadius: "4px",
            cursor: "pointer",
          }}
        >
          Sign in
        </button>
      </form>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run tests/LoginPage.test.tsx 2>&1 | tail -15
```

Expected: all 3 LoginPage tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/src/pages/LoginPage.tsx admin-ui/tests/LoginPage.test.tsx && git commit -m "feat: add LoginPage with token input and error display"
```

---

## Task 11: Frontend — `UserList` + `OrgList` resources

**Files:**
- Create: `admin-ui/src/resources/users/UserList.tsx`
- Create: `admin-ui/src/resources/organizations/OrgList.tsx`

- [ ] **Step 1: Create `admin-ui/src/resources/users/UserList.tsx`**

```tsx
import {
  List,
  Datagrid,
  TextField,
  BooleanField,
  NumberField,
  DateField,
  SearchInput,
  DeleteButton,
  useRecordContext,
  useRefresh,
  useNotify,
  Confirm,
} from "react-admin";
import { useState } from "react";
import { Button } from "@mui/material";
import { adminActions } from "../../dataProvider";
import type { UserRow } from "../../types";

const userFilters = [<SearchInput key="q" source="q" alwaysOn />];

function ToggleEnabledButton() {
  const record = useRecordContext<UserRow>();
  const refresh = useRefresh();
  const notify = useNotify();
  if (!record) return null;
  async function onClick() {
    try {
      if (record!.enabled) {
        await adminActions.disableUser(record!.id);
        notify("User disabled", { type: "info" });
      } else {
        await adminActions.enableUser(record!.id);
        notify("User enabled", { type: "info" });
      }
      refresh();
    } catch (e) {
      notify((e as Error).message, { type: "error" });
    }
  }
  return (
    <Button size="small" onClick={onClick}>
      {record.enabled ? "Disable" : "Enable"}
    </Button>
  );
}

function DeauthButton() {
  const record = useRecordContext<UserRow>();
  const refresh = useRefresh();
  const notify = useNotify();
  const [open, setOpen] = useState(false);
  if (!record) return null;
  async function confirm() {
    try {
      await adminActions.deauthUser(record!.id);
      notify("Sessions cleared", { type: "info" });
      refresh();
    } catch (e) {
      notify((e as Error).message, { type: "error" });
    } finally {
      setOpen(false);
    }
  }
  return (
    <>
      <Button size="small" onClick={() => setOpen(true)}>
        Deauth
      </Button>
      <Confirm
        isOpen={open}
        title="Deauthorize all sessions?"
        content={`This will sign ${record.email} out of all devices.`}
        onConfirm={confirm}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

function RemoveTwoFactorButton() {
  const record = useRecordContext<UserRow>();
  const refresh = useRefresh();
  const notify = useNotify();
  const [open, setOpen] = useState(false);
  if (!record) return null;
  async function confirm() {
    try {
      await adminActions.removeTwoFactor(record!.id);
      notify("Two-factor removed", { type: "info" });
      refresh();
    } catch (e) {
      notify((e as Error).message, { type: "error" });
    } finally {
      setOpen(false);
    }
  }
  return (
    <>
      <Button size="small" disabled={!record.twoFactorEnabled} onClick={() => setOpen(true)}>
        Remove 2FA
      </Button>
      <Confirm
        isOpen={open}
        title="Remove two-factor?"
        content={`This will remove all 2FA methods from ${record.email}.`}
        onConfirm={confirm}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

export function UserList() {
  return (
    <List
      filters={userFilters}
      perPage={25}
      sort={{ field: "email", order: "ASC" }}
    >
      <Datagrid>
        <TextField source="email" />
        <TextField source="name" />
        <BooleanField source="enabled" />
        <BooleanField source="twoFactorEnabled" label="2FA" />
        <NumberField source="cipherCount" label="Ciphers" />
        <NumberField source="attachmentCount" label="Attachments" />
        <DateField source="createdAt" />
        <ToggleEnabledButton />
        <DeauthButton />
        <RemoveTwoFactorButton />
        <DeleteButton mutationMode="pessimistic" />
      </Datagrid>
    </List>
  );
}
```

react-admin's `<Datagrid>` ships `<BulkDeleteButton>` by default when `bulkActionButtons` is not set, which is what we want — selection checkboxes + bulk delete with no custom code.

- [ ] **Step 2: Create `admin-ui/src/resources/organizations/OrgList.tsx`**

```tsx
import {
  List,
  Datagrid,
  TextField,
  NumberField,
  SearchInput,
  DeleteButton,
} from "react-admin";

const orgFilters = [<SearchInput key="q" source="q" alwaysOn />];

export function OrgList() {
  return (
    <List
      filters={orgFilters}
      perPage={25}
      sort={{ field: "name", order: "ASC" }}
    >
      <Datagrid>
        <TextField source="name" />
        <TextField source="billingEmail" />
        <NumberField source="userCount" label="Users" />
        <NumberField source="cipherCount" label="Ciphers" />
        <NumberField source="collectionCount" label="Collections" />
        <DeleteButton mutationMode="pessimistic" />
      </Datagrid>
    </List>
  );
}
```

- [ ] **Step 3: Type-check both files**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx tsc -b 2>&1 | tail -15
```

Expected: no TypeScript errors.

- [ ] **Step 4: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/src/resources/ && git commit -m "feat: add UserList and OrgList resources with row actions and search"
```

---

## Task 12: Frontend — `Settings.tsx` + `Diagnostics.tsx`

**Files:**
- Create: `admin-ui/src/pages/Settings.tsx`
- Create: `admin-ui/src/pages/Diagnostics.tsx`

- [ ] **Step 1: Create `admin-ui/src/pages/Settings.tsx`**

```tsx
import { useEffect, useState } from "react";
import { useNotify, Title } from "react-admin";
import { Box, Card, CardContent, FormControlLabel, Switch, TextField as MuiTextField, Button, Alert } from "@mui/material";
import { adminActions } from "../dataProvider";
import type { AdminSettings } from "../types";

export function Settings() {
  const [settings, setSettings] = useState<AdminSettings | null>(null);
  const [saving, setSaving] = useState(false);
  const notify = useNotify();

  useEffect(() => {
    adminActions
      .getSettings()
      .then(setSettings)
      .catch((e: Error) => notify(e.message, { type: "error" }));
  }, [notify]);

  if (!settings) return <div>Loading…</div>;

  async function onSave() {
    setSaving(true);
    try {
      const updated = await adminActions.saveSettings(settings!);
      setSettings(updated);
      notify("Settings saved (in-memory)", { type: "success" });
    } catch (e) {
      notify((e as Error).message, { type: "error" });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Box p={2}>
      <Title title="Settings" />
      <Card>
        <CardContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            Settings changes are kept in memory only; restart the server to revert.
          </Alert>
          <MuiTextField
            label="Domain"
            fullWidth
            margin="normal"
            value={settings.domain}
            onChange={(e) => setSettings({ ...settings, domain: e.target.value })}
          />
          <FormControlLabel
            control={
              <Switch
                checked={settings.signupsAllowed}
                onChange={(e) => setSettings({ ...settings, signupsAllowed: e.target.checked })}
              />
            }
            label="Allow signups"
          />
          <FormControlLabel
            control={
              <Switch
                checked={settings.invitationsAllowed}
                onChange={(e) => setSettings({ ...settings, invitationsAllowed: e.target.checked })}
              />
            }
            label="Allow invitations"
          />
          <MuiTextField
            label="Password iterations"
            type="number"
            fullWidth
            margin="normal"
            value={settings.passwordIterations}
            disabled
            helperText="Read-only; changes apply at signup only."
          />
          <MuiTextField
            label="Mail from"
            fullWidth
            margin="normal"
            value={settings.mail.from}
            disabled
          />
          <Box mt={2}>
            <Button variant="contained" onClick={onSave} disabled={saving}>
              {saving ? "Saving…" : "Save"}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
```

- [ ] **Step 2: Create `admin-ui/src/pages/Diagnostics.tsx`**

```tsx
import { useEffect, useState, useCallback } from "react";
import { useNotify, Title } from "react-admin";
import { Box, Card, CardContent, Table, TableBody, TableCell, TableRow, Button } from "@mui/material";
import { adminActions } from "../dataProvider";
import type { Diagnostics as DiagnosticsData } from "../types";

export function Diagnostics() {
  const [data, setData] = useState<DiagnosticsData | null>(null);
  const notify = useNotify();

  const load = useCallback(() => {
    adminActions
      .getDiagnostics()
      .then(setData)
      .catch((e: Error) => notify(e.message, { type: "error" }));
  }, [notify]);

  useEffect(() => {
    load();
  }, [load]);

  if (!data) return <div>Loading…</div>;

  return (
    <Box p={2}>
      <Title title="Diagnostics" />
      <Card>
        <CardContent>
          <Box mb={1}>
            <Button onClick={load} variant="outlined" size="small">
              Refresh
            </Button>
          </Box>
          <Table>
            <TableBody>
              {Object.entries(data).map(([k, v]) => (
                <TableRow key={k}>
                  <TableCell sx={{ fontWeight: 600, width: "30%" }}>{k}</TableCell>
                  <TableCell>{String(v)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </Box>
  );
}
```

- [ ] **Step 3: Type-check**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx tsc -b 2>&1 | tail -10
```

Expected: no TypeScript errors.

- [ ] **Step 4: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/src/pages/Settings.tsx admin-ui/src/pages/Diagnostics.tsx && git commit -m "feat: add Settings and Diagnostics custom routes"
```

---

## Task 13: Frontend — `App.tsx` wiring + `Layout.tsx` + `i18n.ts` + README

**Files:**
- Modify: `admin-ui/src/App.tsx`
- Create: `admin-ui/src/Layout.tsx`
- Create: `admin-ui/src/i18n.ts`
- Create: `admin-ui/README.md`

- [ ] **Step 1: Create `admin-ui/src/Layout.tsx`**

```tsx
import { Layout as RALayout, Menu, MenuItemLink } from "react-admin";
import type { LayoutProps } from "react-admin";
import PeopleIcon from "@mui/icons-material/People";
import BusinessIcon from "@mui/icons-material/Business";
import SettingsIcon from "@mui/icons-material/Settings";
import InfoIcon from "@mui/icons-material/Info";

function AdminMenu() {
  return (
    <Menu>
      <MenuItemLink to="/users" primaryText="Users" leftIcon={<PeopleIcon />} />
      <MenuItemLink to="/organizations" primaryText="Organizations" leftIcon={<BusinessIcon />} />
      <MenuItemLink to="/settings" primaryText="Settings" leftIcon={<SettingsIcon />} />
      <MenuItemLink to="/diagnostics" primaryText="Diagnostics" leftIcon={<InfoIcon />} />
    </Menu>
  );
}

export function Layout(props: LayoutProps) {
  return <RALayout {...props} menu={AdminMenu} />;
}
```

- [ ] **Step 2: Create `admin-ui/src/i18n.ts`**

```ts
import polyglotI18nProvider from "ra-i18n-polyglot";
import englishMessages from "ra-language-english";

const messages = {
  ...englishMessages,
  resources: {
    users: { name: "User |||| Users" },
    organizations: { name: "Organization |||| Organizations" },
  },
};

export const i18nProvider = polyglotI18nProvider(() => messages, "en", [
  { locale: "en", name: "English" },
]);
```

Add the two new packages to `admin-ui/package.json` under `dependencies`:

```json
"ra-i18n-polyglot": "^5.4.0",
"ra-language-english": "^5.4.0"
```

Then install:

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npm install 2>&1 | tail -3
```

- [ ] **Step 3: Replace `admin-ui/src/App.tsx`**

```tsx
import { Admin, Resource, CustomRoutes } from "react-admin";
import { Route } from "react-router-dom";
import { dataProvider } from "./dataProvider";
import { authProvider } from "./authProvider";
import { Layout } from "./Layout";
import { i18nProvider } from "./i18n";
import { LoginPage } from "./pages/LoginPage";
import { Settings } from "./pages/Settings";
import { Diagnostics } from "./pages/Diagnostics";
import { UserList } from "./resources/users/UserList";
import { OrgList } from "./resources/organizations/OrgList";

export function App() {
  return (
    <Admin
      title="VaultGuard Admin"
      dataProvider={dataProvider}
      authProvider={authProvider}
      loginPage={LoginPage}
      layout={Layout}
      i18nProvider={i18nProvider}
      disableTelemetry
    >
      <Resource name="users" list={UserList} options={{ label: "Users" }} />
      <Resource name="organizations" list={OrgList} options={{ label: "Organizations" }} />
      <CustomRoutes>
        <Route path="/settings" element={<Settings />} />
        <Route path="/diagnostics" element={<Diagnostics />} />
      </CustomRoutes>
    </Admin>
  );
}
```

- [ ] **Step 4: Create `admin-ui/README.md`**

```markdown
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
```

- [ ] **Step 5: Type-check + unit tests**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx tsc -b 2>&1 | tail -10
```

Expected: no TypeScript errors.

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npx vitest run 2>&1 | tail -15
```

Expected: all tests pass.

- [ ] **Step 6: Production build**

```bash
cd /Users/ho/workspace/github/vaultguard/admin-ui && npm run build 2>&1 | tail -15
```

Expected: build emits `dist/index.html`, `dist/assets/*.js`, `dist/assets/*.css`. No errors.

- [ ] **Step 7: Commit**

```bash
cd /Users/ho/workspace/github/vaultguard && git add admin-ui/src/App.tsx admin-ui/src/Layout.tsx admin-ui/src/i18n.ts admin-ui/README.md admin-ui/package.json admin-ui/package-lock.json && git commit -m "feat: wire <Admin> shell with resources, custom routes, layout, and i18n"
```

---

## Self-Review

### Spec coverage

| Spec section / requirement | Implemented by |
|----------------------------|----------------|
| `adminCorsOrigins` property                            | Task 1 |
| Pagination, sort, filter on `/api/admin/users`         | Task 2 |
| `X-Total-Count` header on `/api/admin/users`           | Task 2 |
| Pagination, sort, filter on `/api/admin/organizations` | Task 3 |
| `X-Total-Count` on `/api/admin/organizations`          | Task 3 |
| CORS for `/api/admin/**` + preflight OPTIONS bypass    | Task 4 |
| Vite/React/TS scaffold                                 | Task 5 |
| `types.ts`                                             | Task 6 |
| `httpClient.ts` + tests                                | Task 6 |
| `dataProvider.ts` CRUD methods                         | Task 7 |
| `dataProvider.ts` custom action methods                | Task 8 |
| `authProvider.ts` + tests                              | Task 9 |
| `LoginPage.tsx` + tests                                | Task 10 |
| `UserList.tsx` + row actions + bulk delete             | Task 11 |
| `OrgList.tsx` + bulk delete                            | Task 11 |
| `Settings.tsx`                                         | Task 12 |
| `Diagnostics.tsx`                                      | Task 12 |
| `App.tsx` + `Layout.tsx` + Menu                        | Task 13 |
| `i18n.ts`                                              | Task 13 |
| README                                                 | Task 13 |

### Placeholder scan

No TBD / TODO / "implement later" / "similar to Task N" in any step. Every
step that changes code shows the full code; every test step shows the
assertion; every command step shows the full command and expected output.

### Type consistency

- `TOKEN_KEY` is defined once in `types.ts` and re-exported from `httpClient.ts`.
  Both modules use the same string `"vaultguardAdminToken"`.
- `adminActions` method names match what `UserList`, `Settings`, and
  `Diagnostics` call: `disableUser`, `enableUser`, `deauthUser`,
  `removeTwoFactor`, `getSettings`, `saveSettings`, `getDiagnostics`.
- `AdminPage<T>(data, total)` is consumed by `AdminController` exactly as
  produced by `AdminService`.
- Backend list responses keep their array body (legacy admin compatible);
  total moves to `X-Total-Count` header.

### Risks / known limitations

- The plan assumes Spring Data's derived-query method naming for case-insensitive
  contains works on H2 (test DB) and SQLite (dev DB) — both support it via JPA's
  `LOWER(...) LIKE` translation. PostgreSQL and MySQL also support it.
- The plan assumes `AdminAuthFilter` extends `OncePerRequestFilter`. If it
  uses a different filter base class, the OPTIONS short-circuit (Task 4
  Step 4) must still happen before any 401 response — adapt accordingly.
- react-admin v5 ships `@tanstack/react-query` as a peer dependency. Task 10
  adds it explicitly so component tests can render `<Admin>` context.
