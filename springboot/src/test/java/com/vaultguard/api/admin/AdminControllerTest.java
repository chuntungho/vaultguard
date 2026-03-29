package com.vaultguard.api.admin;

import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired PasswordHashService passwordHashService;

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        user.setUuid(UuidUtil.newUuid());
        user.setEmail("admin-test@example.com");
        user.setName("Admin Test");
        user.setPasswordHash(passwordHashService.hashForStorage("hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
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
}
