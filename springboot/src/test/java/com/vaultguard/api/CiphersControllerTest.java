package com.vaultguard.api;

import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.vaultguard.auth.JwtService;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.CipherRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class CiphersControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CipherRepository cipherRepository;
    @Autowired PasswordHashService passwordHashService;
    @Autowired JwtService jwtService;

    private String userUuid;
    private String authToken;

    @BeforeEach
    void setUp() {
        cipherRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        userUuid = UuidUtil.newUuid();
        user.setUuid(userUuid);
        user.setEmail("cipher-test@example.com");
        user.setName("Cipher Test");
        user.setPasswordHash(passwordHashService.hashForStorage("hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
        authToken = jwtService.issueAccessToken(userUuid, UuidUtil.newUuid());
    }

    @Test
    void createAndGetCipher() throws Exception {
        Map<String, Object> body = Map.of(
            "type", 1,
            "name", "2.encrypted-name==",
            "login", Map.of("username", "2.encrypted-user==", "password", "2.encrypted-pass==")
        );

        String response = mockMvc.perform(post("/api/ciphers")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Id").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        String cipherId = objectMapper.readTree(response).get("Id").asText();

        mockMvc.perform(get("/api/ciphers/" + cipherId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Id").value(cipherId));
    }

    @Test
    void deleteCipher() throws Exception {
        Map<String, Object> body = Map.of("type", 2, "name", "2.note==",
            "secureNote", Map.of("type", 0));
        String response = mockMvc.perform(post("/api/ciphers")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String cipherId = objectMapper.readTree(response).get("Id").asText();

        // 200 with empty body, matching the Rust EmptyResult contract
        mockMvc.perform(delete("/api/ciphers/" + cipherId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/ciphers/" + cipherId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isNotFound());
    }
}
