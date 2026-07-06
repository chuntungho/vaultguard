package com.vaultguard.service;

import tools.jackson.databind.ObjectMapper;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Attachment;
import com.vaultguard.db.entity.Cipher;
import com.vaultguard.db.entity.CollectionCipher;
import com.vaultguard.db.repository.AttachmentRepository;
import com.vaultguard.db.repository.CollectionCipherRepository;
import com.vaultguard.db.repository.FavoriteRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the Bitwarden cipher response envelope (shared by the ciphers API and /api/sync),
 * mirroring the fields produced by Cipher::to_json in the Rust implementation.
 */
@Component
public class CipherResponseMapper {

    private final FavoriteRepository favoriteRepository;
    private final AttachmentRepository attachmentRepository;
    private final CollectionCipherRepository collectionCipherRepository;
    private final VaultGuardProperties props;
    private final ObjectMapper objectMapper;

    public CipherResponseMapper(FavoriteRepository favoriteRepository,
                                AttachmentRepository attachmentRepository,
                                CollectionCipherRepository collectionCipherRepository,
                                VaultGuardProperties props,
                                ObjectMapper objectMapper) {
        this.favoriteRepository = favoriteRepository;
        this.attachmentRepository = attachmentRepository;
        this.collectionCipherRepository = collectionCipherRepository;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toResponse(Cipher cipher, String userUuid) {
        boolean favorite = favoriteRepository.existsByUserUuidAndCipherUuid(userUuid, cipher.getUuid());
        List<Attachment> attachments = attachmentRepository.findByCipherUuid(cipher.getUuid());
        List<String> collectionIds = collectionCipherRepository.findByCipherUuid(cipher.getUuid())
            .stream().map(CollectionCipher::getCollectionUuid).toList();
        return build(cipher, favorite, attachments, collectionIds);
    }

    private Map<String, Object> build(Cipher cipher, boolean favorite,
                                      List<Attachment> attachments, List<String> collectionIds) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", cipher.getUuid());
        resp.put("Type", cipher.getType());
        resp.put("Name", cipher.getName());
        resp.put("Notes", cipher.getNotes());
        resp.put("FolderId", cipher.getFolderUuid());
        resp.put("OrganizationId", cipher.getOrganizationUuid());
        resp.put("Favorite", favorite);
        resp.put("Reprompt", cipher.getReprompt());
        resp.put("Edit", true);
        resp.put("ViewPassword", true);
        resp.put("OrganizationUseTotp", true);

        Object data = parseJson(cipher.getData());
        resp.put("Data", data);
        String typeKey = switch (cipher.getType()) {
            case 1 -> "Login";
            case 2 -> "SecureNote";
            case 3 -> "Card";
            case 4 -> "Identity";
            default -> null;
        };
        if (typeKey != null) {
            resp.put(typeKey, data);
        }

        resp.put("Fields", parseJson(cipher.getFields()));
        resp.put("PasswordHistory", parseJson(cipher.getPasswordHistory()));
        resp.put("Key", cipher.getKey());
        resp.put("Attachments", attachments.isEmpty() ? null : attachments.stream()
            .map(a -> toAttachmentResponse(a, cipher.getUuid())).toList());
        resp.put("CollectionIds", collectionIds);
        resp.put("RevisionDate", cipher.getUpdatedAt());
        resp.put("CreationDate", cipher.getCreatedAt());
        resp.put("DeletedDate", cipher.getDeletedDate());
        resp.put("Object", "cipherDetails");
        return resp;
    }

    public Map<String, Object> toAttachmentResponse(Attachment att, String cipherId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", att.getId());
        resp.put("FileName", att.getFileName());
        resp.put("Size", String.valueOf(att.getFileSize()));
        resp.put("SizeName", humanSize(att.getFileSize()));
        resp.put("Key", att.getAkey());
        resp.put("Url", props.getDomain() + "/api/ciphers/" + cipherId + "/attachment/" + att.getId());
        resp.put("Object", "attachment");
        return resp;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}
