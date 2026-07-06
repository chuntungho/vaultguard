package com.vaultguard.api;

import tools.jackson.databind.ObjectMapper;
import com.vaultguard.auth.JwtService;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevicesAndMetaTest {

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
        user.setEmail("device@example.com");
        user.setName("Device User");
        user.setPasswordHash(passwordHashService.hashForStorage("master-hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
        authToken = jwtService.issueAccessToken(userUuid, UuidUtil.newUuid());
    }

    private void login(String deviceIdentifier) throws Exception {
        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "device@example.com")
            .param("password", "master-hash")
            .param("deviceIdentifier", deviceIdentifier)
            .param("deviceName", "Test Browser")
            .param("deviceType", "3"))
            .andExpect(status().isOk());
    }

    @Test
    void knownDeviceLifecycle() throws Exception {
        String deviceId = UuidUtil.newUuid();
        String emailB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("device@example.com".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/devices/knowndevice")
            .header("X-Device-Identifier", deviceId)
            .header("X-Request-Email", emailB64))
            .andExpect(status().isOk())
            .andExpect(content().string("false"));

        login(deviceId);

        mockMvc.perform(get("/api/devices/knowndevice")
            .header("X-Device-Identifier", deviceId)
            .header("X-Request-Email", emailB64))
            .andExpect(status().isOk())
            .andExpect(content().string("true"));

        // The device row is keyed by the client identifier and keeps the device name
        var device = deviceRepository.findByUuidAndUserUuid(deviceId, userUuid).orElseThrow();
        assertThat(device.getName()).isEqualTo("Test Browser");

        mockMvc.perform(get("/api/devices")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Data[0].Identifier").value(deviceId));

        mockMvc.perform(get("/api/devices/identifier/" + deviceId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Name").value("Test Browser"));
    }

    @Test
    void configIsServedUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").isNotEmpty())
            .andExpect(jsonPath("$.environment.api").isNotEmpty())
            .andExpect(jsonPath("$.object").value("config"));

        mockMvc.perform(get("/api/alive")).andExpect(status().isOk());
    }

    @Test
    void accountHelperEndpoints() throws Exception {
        mockMvc.perform(get("/api/accounts/revision-date")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/verify-password")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("masterPasswordHash", "master-hash"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/verify-password")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("masterPasswordHash", "nope"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmailTokenMarksUserVerified() throws Exception {
        String token = jwtService.issuePurposeToken(userUuid, "verifyemail", 3600);

        mockMvc.perform(post("/api/accounts/verify-email-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("userId", userUuid, "token", token))))
            .andExpect(status().isOk());

        assertThat(userRepository.findById(userUuid).orElseThrow().getVerifiedAt())
            .isNotNull()
            .isBeforeOrEqualTo(Instant.now());

        // A purpose token must never be usable as an access token
        mockMvc.perform(get("/api/accounts/profile")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void securityStampRotationInvalidatesLoginTokens() throws Exception {
        String deviceId = UuidUtil.newUuid();
        String response = mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "device@example.com")
            .param("password", "master-hash")
            .param("deviceIdentifier", deviceId)
            .param("deviceName", "Test Browser")
            .param("deviceType", "3"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String loginToken = objectMapper.readTree(response).get("access_token").asText();

        mockMvc.perform(get("/api/accounts/profile")
            .header("Authorization", "Bearer " + loginToken))
            .andExpect(status().isOk());

        User user = userRepository.findById(userUuid).orElseThrow();
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);

        mockMvc.perform(get("/api/accounts/profile")
            .header("Authorization", "Bearer " + loginToken))
            .andExpect(status().isForbidden());
    }
}
