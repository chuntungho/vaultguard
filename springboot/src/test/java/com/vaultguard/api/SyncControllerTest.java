package com.vaultguard.api;

import com.vaultguard.auth.JwtService;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.CipherRepository;
import com.vaultguard.db.repository.FolderRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CipherRepository cipherRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired PasswordHashService passwordHashService;
    @Autowired JwtService jwtService;

    private String userUuid;
    private String authToken;

    @BeforeEach
    void setUp() {
        cipherRepository.deleteAll();
        folderRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        userUuid = UuidUtil.newUuid();
        user.setUuid(userUuid);
        user.setEmail("sync-test@example.com");
        user.setName("Sync Test");
        user.setPasswordHash(passwordHashService.hashForStorage("hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
        authToken = jwtService.issueAccessToken(userUuid, UuidUtil.newUuid());
    }

    @Test
    void syncReturnsProfileAndEmptyVault() throws Exception {
        mockMvc.perform(get("/api/sync")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Profile.Id").value(userUuid))
            .andExpect(jsonPath("$.Ciphers").isArray())
            .andExpect(jsonPath("$.Folders").isArray())
            .andExpect(jsonPath("$.Object").value("sync"));
    }
}
