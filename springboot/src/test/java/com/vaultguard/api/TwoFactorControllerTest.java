package com.vaultguard.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.auth.JwtService;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.db.repository.TwoFactorRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.util.UuidUtil;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired TwoFactorRepository twoFactorRepository;
    @Autowired PasswordHashService passwordHashService;
    @Autowired JwtService jwtService;

    private String userUuid;
    private String authToken;

    @BeforeEach
    void setUp() {
        twoFactorRepository.deleteAll();
        deviceRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        userUuid = UuidUtil.newUuid();
        user.setUuid(userUuid);
        user.setEmail("twofactor@example.com");
        user.setName("2FA User");
        user.setPasswordHash(passwordHashService.hashForStorage("master-hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
        authToken = jwtService.issueAccessToken(userUuid, UuidUtil.newUuid());
    }

    @AfterEach
    void tearDown() {
        // twofactor rows reference users; leaving them behind breaks other classes' cleanup
        twoFactorRepository.deleteAll();
        deviceRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String currentTotp(String secret) throws Exception {
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        long timeStep = new SystemTimeProvider().getTime() / 30;
        return codeGenerator.generate(secret, timeStep);
    }

    private String enableTotp() throws Exception {
        String response = mockMvc.perform(post("/api/two-factor/get-authenticator")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("masterPasswordHash", "master-hash"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false))
            .andReturn().getResponse().getContentAsString();
        String key = objectMapper.readTree(response).get("key").asText();

        mockMvc.perform(post("/api/two-factor/authenticator")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "masterPasswordHash", "master-hash",
                "key", key,
                "token", currentTotp(key)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
        return key;
    }

    @Test
    void totpSetupListAndDisable() throws Exception {
        enableTotp();

        mockMvc.perform(get("/api/two-factor")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].type").value(0))
            .andExpect(jsonPath("$.data[0].enabled").value(true));

        // Enabling 2FA generates a recovery code
        mockMvc.perform(post("/api/two-factor/get-recover")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("masterPasswordHash", "master-hash"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").isNotEmpty());

        mockMvc.perform(post("/api/two-factor/disable")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "masterPasswordHash", "master-hash", "type", 0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));

        assertThat(twoFactorRepository.findByUserUuid(userUuid)).isEmpty();
    }

    @Test
    void loginRequiresTotpAndAcceptsValidCode() throws Exception {
        String key = enableTotp();

        // Without a token: 400 with the provider list
        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "twofactor@example.com")
            .param("password", "master-hash")
            .param("deviceIdentifier", UuidUtil.newUuid())
            .param("deviceName", "test")
            .param("deviceType", "3"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"))
            .andExpect(jsonPath("$.TwoFactorProviders[0]").value("0"))
            .andExpect(jsonPath("$.TwoFactorProviders2").exists());

        // With a valid TOTP code: success
        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "twofactor@example.com")
            .param("password", "master-hash")
            .param("twoFactorProvider", "0")
            .param("twoFactorToken", currentTotp(key))
            .param("deviceIdentifier", UuidUtil.newUuid())
            .param("deviceName", "test")
            .param("deviceType", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    void recoveryCodeLoginRemovesAllTwoFactor() throws Exception {
        enableTotp();
        String recoveryCode = userRepository.findById(userUuid).orElseThrow().getTotpRecover();
        assertThat(recoveryCode).isNotBlank();

        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "twofactor@example.com")
            .param("password", "master-hash")
            .param("twoFactorProvider", "8")
            .param("twoFactorToken", recoveryCode)
            .param("deviceIdentifier", UuidUtil.newUuid())
            .param("deviceName", "test")
            .param("deviceType", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty());

        assertThat(twoFactorRepository.findByUserUuid(userUuid)).isEmpty();
        assertThat(userRepository.findById(userUuid).orElseThrow().getTotpRecover()).isNull();
    }

    @Test
    void loginResponseIncludesDecryptionMaterial() throws Exception {
        User user = userRepository.findById(userUuid).orElseThrow();
        user.setKeyHash("2.user-key==");
        user.setPrivateKey("2.private-key==");
        userRepository.save(user);

        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "twofactor@example.com")
            .param("password", "master-hash")
            .param("deviceIdentifier", UuidUtil.newUuid())
            .param("deviceName", "test")
            .param("deviceType", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Key").value("2.user-key=="))
            .andExpect(jsonPath("$.PrivateKey").value("2.private-key=="))
            .andExpect(jsonPath("$.Kdf").value(0))
            .andExpect(jsonPath("$.KdfIterations").value(600000))
            .andExpect(jsonPath("$.scope").value("api offline_access"));
    }
}
