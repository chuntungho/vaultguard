package com.vaultguard.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.auth.JwtService;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired PasswordHashService passwordHashService;
    @Autowired JwtService jwtService;

    private String userUuid;
    private String authToken;

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        userUuid = UuidUtil.newUuid();
        user.setUuid(userUuid);
        user.setEmail("existing@example.com");
        user.setName("Existing User");
        user.setPasswordHash(passwordHashService.hashForStorage("hash"));
        user.setKdfType(0);
        user.setKdfIterations(600000);
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
        authToken = jwtService.issueAccessToken(userUuid, UuidUtil.newUuid());
    }

    @Test
    void registerCreatesUser() throws Exception {
        Map<String, Object> body = Map.of(
            "email", "newuser@example.com",
            "name", "New User",
            "masterPasswordHash", "hashed-password",
            "kdf", 0,
            "kdfIterations", 600000,
            "key", "protected-symmetric-key"
        );
        mockMvc.perform(post("/api/accounts/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());
    }

    @Test
    void preloginReturnsKdfInfo() throws Exception {
        mockMvc.perform(post("/api/accounts/prelogin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"existing@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Kdf").value(0))
            .andExpect(jsonPath("$.KdfIterations").value(600000));
    }

    @Test
    void getProfileRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/accounts/profile"))
            .andExpect(status().isForbidden());
    }

    @Test
    void getProfileWithTokenReturnsUser() throws Exception {
        mockMvc.perform(get("/api/accounts/profile")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Id").value(userUuid))
            .andExpect(jsonPath("$.Email").value("existing@example.com"));
    }
}
