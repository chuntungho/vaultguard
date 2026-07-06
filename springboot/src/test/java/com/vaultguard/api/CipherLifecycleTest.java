package com.vaultguard.api;

import tools.jackson.databind.ObjectMapper;
import com.vaultguard.auth.JwtService;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.*;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CipherLifecycleTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired CipherRepository cipherRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired PasswordHashService passwordHashService;
    @Autowired JwtService jwtService;

    private String userUuid;
    private String authToken;

    @BeforeEach
    void setUp() {
        favoriteRepository.deleteAll();
        cipherRepository.deleteAll();
        folderRepository.deleteAll();
        deviceRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        userUuid = UuidUtil.newUuid();
        user.setUuid(userUuid);
        user.setEmail("lifecycle@example.com");
        user.setName("Lifecycle User");
        user.setPasswordHash(passwordHashService.hashForStorage("master-hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
        authToken = jwtService.issueAccessToken(userUuid, UuidUtil.newUuid());
    }

    private String createCipher(String name) throws Exception {
        String response = mockMvc.perform(post("/api/ciphers")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "type", 1, "name", name,
                "login", Map.of("username", "2.user==", "password", "2.pass==")))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("Id").asText();
    }

    @Test
    void softDeleteAndRestore() throws Exception {
        String id = createCipher("2.trash-me==");

        mockMvc.perform(put("/api/ciphers/" + id + "/delete")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk());

        // Still visible with DeletedDate set (trash)
        mockMvc.perform(get("/api/ciphers/" + id)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.DeletedDate").isNotEmpty());

        mockMvc.perform(put("/api/ciphers/" + id + "/restore")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.DeletedDate").isEmpty());
    }

    @Test
    void bulkSoftDeleteThenBulkRestore() throws Exception {
        String id1 = createCipher("2.one==");
        String id2 = createCipher("2.two==");

        mockMvc.perform(put("/api/ciphers/delete")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("ids", List.of(id1, id2)))))
            .andExpect(status().isOk());

        assertThat(cipherRepository.findById(id1).orElseThrow().getDeletedDate()).isNotNull();
        assertThat(cipherRepository.findById(id2).orElseThrow().getDeletedDate()).isNotNull();

        mockMvc.perform(put("/api/ciphers/restore")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("ids", List.of(id1, id2)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Data.length()").value(2));

        assertThat(cipherRepository.findById(id1).orElseThrow().getDeletedDate()).isNull();
    }

    @Test
    void moveCiphersIntoFolderAndFolderDeleteUnfiles() throws Exception {
        String cipherId = createCipher("2.filed==");
        String folderResponse = mockMvc.perform(post("/api/folders")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("name", "2.folder=="))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String folderId = objectMapper.readTree(folderResponse).get("Id").asText();

        mockMvc.perform(post("/api/ciphers/move")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "ids", List.of(cipherId), "folderId", folderId))))
            .andExpect(status().isOk());
        assertThat(cipherRepository.findById(cipherId).orElseThrow().getFolderUuid())
            .isEqualTo(folderId);

        // Deleting the folder moves the cipher out but keeps it
        mockMvc.perform(delete("/api/folders/" + folderId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk());
        assertThat(cipherRepository.findById(cipherId).orElseThrow().getFolderUuid()).isNull();
    }

    @Test
    void partialUpdateSetsFavorite() throws Exception {
        String id = createCipher("2.favorite-me==");

        mockMvc.perform(put("/api/ciphers/" + id + "/partial")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("favorite", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Favorite").value(true));

        assertThat(favoriteRepository.existsByUserUuidAndCipherUuid(userUuid, id)).isTrue();

        mockMvc.perform(put("/api/ciphers/" + id + "/partial")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("favorite", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Favorite").value(false));
    }

    @Test
    void importCreatesFoldersAndLinksCiphers() throws Exception {
        Map<String, Object> body = Map.of(
            "folders", List.of(Map.of("name", "2.imported-folder==")),
            "ciphers", List.of(
                Map.of("type", 1, "name", "2.in-folder==",
                    "login", Map.of("username", "2.u==")),
                Map.of("type", 2, "name", "2.loose-note==",
                    "secureNote", Map.of("type", 0))),
            "folderRelationships", List.of(Map.of("key", 0, "value", 0)));

        mockMvc.perform(post("/api/ciphers/import")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());

        assertThat(folderRepository.findByUserUuid(userUuid)).hasSize(1);
        assertThat(cipherRepository.findByUserUuid(userUuid)).hasSize(2);
        String folderId = folderRepository.findByUserUuid(userUuid).get(0).getUuid();
        assertThat(cipherRepository.findByUserUuid(userUuid).stream()
            .filter(c -> folderId.equals(c.getFolderUuid()))).hasSize(1);
    }

    @Test
    void purgeDeletesEverythingAfterPasswordCheck() throws Exception {
        createCipher("2.purge-me==");

        mockMvc.perform(post("/api/ciphers/purge")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("masterPasswordHash", "wrong"))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/ciphers/purge")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("masterPasswordHash", "master-hash"))))
            .andExpect(status().isOk());

        assertThat(cipherRepository.findByUserUuid(userUuid)).isEmpty();
    }
}
