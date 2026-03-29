package com.vaultguard.api;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityControllerTest {

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
        user.setEmail("login@example.com");
        user.setName("Login User");
        user.setPasswordHash(passwordHashService.hashForStorage("correct-hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "login@example.com")
            .param("password", "correct-hash")
            .param("scope", "api offline_access")
            .param("client_id", "browser")
            .param("deviceType", "3")
            .param("deviceIdentifier", UuidUtil.newUuid())
            .param("deviceName", "Test Browser"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    void loginWithInvalidPasswordReturns400() throws Exception {
        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "login@example.com")
            .param("password", "wrong-hash")
            .param("scope", "api offline_access")
            .param("client_id", "browser")
            .param("deviceType", "3")
            .param("deviceIdentifier", UuidUtil.newUuid())
            .param("deviceName", "Test Browser"))
            .andExpect(status().isBadRequest());
    }
}
