package com.vaultguard.api.admin;

import com.vaultguard.db.entity.Organization;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.CipherRepository;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.db.repository.OrganizationRepository;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired CipherRepository cipherRepository;
    @Autowired PasswordHashService passwordHashService;
    @Autowired OrganizationRepository organizationRepository;

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

        organizationRepository.deleteAll();
        String[] orgNames = {"Acme Inc", "Bravo LLC", "Charlie Corp"};
        for (String n : orgNames) {
            Organization org = new Organization();
            org.setUuid(UuidUtil.newUuid());
            org.setName(n);
            org.setBillingEmail(n.split(" ")[0].toLowerCase() + "@example.com");
            organizationRepository.save(org);
        }
    }

    @Test
    void adminUsersRequiresToken() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUsersWithValidTokenReturnsUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminUsersWithWrongTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users")
            .header("X-Admin-Token", "wrong-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUsersReturnUserDetails() throws Exception {
        mockMvc.perform(get("/api/admin/users")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").isNotEmpty())
            .andExpect(jsonPath("$[0].cipherCount").exists());
    }

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

    @Test
    void adminDisableAndEnableUser() throws Exception {
        String uuid = userRepository.findAll().get(0).getUuid();

        mockMvc.perform(post("/api/admin/users/" + uuid + "/disable")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/admin/users/" + uuid + "/enable")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void adminUsersPaginationSlices() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=2&sort=email,asc")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("alpha@example.com"))
            .andExpect(jsonPath("$[1].email").value("bravo@example.com"))
            .andExpect(header().string("X-Total-Count", "5"));
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
            .andExpect(header().string("X-Total-Count", "1"));
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
            .andExpect(header().string("X-Total-Count", "5"));
    }

    @Test
    void adminOrgsList() throws Exception {
        mockMvc.perform(get("/api/admin/organizations")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(header().string("X-Total-Count", "3"));
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

    @Test
    void corsPreflightAllowedForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/admin/users")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "X-Admin-Token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Total-Count")));
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
    void corsActualGetIncludesAllowOriginAndExposeHeaders() throws Exception {
        mockMvc.perform(get("/api/admin/users")
            .header("Origin", "http://localhost:5173")
            .header("X-Admin-Token", "test-admin-token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Total-Count")));
    }
}
