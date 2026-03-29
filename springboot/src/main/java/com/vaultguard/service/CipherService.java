package com.vaultguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.db.entity.Cipher;
import com.vaultguard.db.repository.CipherRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CipherService {

    private final CipherRepository cipherRepository;
    private final ObjectMapper objectMapper;

    public CipherService(CipherRepository cipherRepository, ObjectMapper objectMapper) {
        this.cipherRepository = cipherRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Cipher create(String userUuid, Map<String, Object> data) {
        Cipher cipher = new Cipher();
        cipher.setUuid(UuidUtil.newUuid());
        cipher.setUserUuid(userUuid);
        applyData(cipher, data);
        return cipherRepository.save(cipher);
    }

    @Transactional
    public Cipher update(Cipher cipher, Map<String, Object> data) {
        applyData(cipher, data);
        return cipherRepository.save(cipher);
    }

    @Transactional
    public void delete(Cipher cipher) {
        cipherRepository.delete(cipher);
    }

    public Optional<Cipher> findById(String uuid) {
        return cipherRepository.findById(uuid);
    }

    public List<Cipher> findByUserUuid(String userUuid) {
        return cipherRepository.findByUserUuid(userUuid);
    }

    public List<Cipher> findAllAccessibleByUser(String userUuid) {
        return cipherRepository.findAllAccessibleByUser(userUuid);
    }

    private void applyData(Cipher cipher, Map<String, Object> data) {
        cipher.setType(((Number) data.getOrDefault("type", 1)).intValue());
        cipher.setName((String) data.get("name"));
        cipher.setNotes((String) data.get("notes"));
        cipher.setFolderUuid((String) data.get("folderId"));
        if (data.containsKey("reprompt")) {
            cipher.setReprompt(((Number) data.get("reprompt")).intValue());
        }
        try {
            String typeKey = switch (cipher.getType()) {
                case 1 -> "login";
                case 2 -> "secureNote";
                case 3 -> "card";
                case 4 -> "identity";
                default -> "data";
            };
            Object typeData = data.get(typeKey);
            cipher.setData(typeData != null
                ? objectMapper.writeValueAsString(typeData)
                : "{}");
            if (data.containsKey("fields")) {
                cipher.setFields(objectMapper.writeValueAsString(data.get("fields")));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cipher data", e);
        }
    }
}
