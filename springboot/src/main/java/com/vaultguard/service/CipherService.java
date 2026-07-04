package com.vaultguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.db.entity.Cipher;
import com.vaultguard.db.entity.CollectionCipher;
import com.vaultguard.db.entity.Favorite;
import com.vaultguard.db.entity.Folder;
import com.vaultguard.db.repository.*;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CipherService {

    private final CipherRepository cipherRepository;
    private final FavoriteRepository favoriteRepository;
    private final FolderRepository folderRepository;
    private final CollectionRepository collectionRepository;
    private final CollectionCipherRepository collectionCipherRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final AttachmentService attachmentService;
    private final ObjectMapper objectMapper;

    public CipherService(CipherRepository cipherRepository,
                         FavoriteRepository favoriteRepository,
                         FolderRepository folderRepository,
                         CollectionRepository collectionRepository,
                         CollectionCipherRepository collectionCipherRepository,
                         OrgMembershipRepository orgMembershipRepository,
                         AttachmentService attachmentService,
                         ObjectMapper objectMapper) {
        this.cipherRepository = cipherRepository;
        this.favoriteRepository = favoriteRepository;
        this.folderRepository = folderRepository;
        this.collectionRepository = collectionRepository;
        this.collectionCipherRepository = collectionCipherRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.attachmentService = attachmentService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Cipher create(String userUuid, Map<String, Object> data) {
        Cipher cipher = new Cipher();
        cipher.setUuid(UuidUtil.newUuid());
        cipher.setUserUuid(userUuid);
        applyData(cipher, data, userUuid);
        return cipherRepository.save(cipher);
    }

    /**
     * The /ciphers/create form: {cipher, collectionIds}. Organization ciphers must be
     * created through this endpoint and assigned to at least one collection.
     */
    @Transactional
    public Cipher createWithCollections(String userUuid, Map<String, Object> cipherData,
                                        List<String> collectionIds) {
        String orgId = (String) cipherData.get("organizationId");
        Cipher cipher = new Cipher();
        cipher.setUuid(UuidUtil.newUuid());
        if (orgId != null && !orgId.isBlank()) {
            requireConfirmedMembership(userUuid, orgId);
            if (collectionIds == null || collectionIds.isEmpty()) {
                throw new IllegalArgumentException("You must select at least one collection.");
            }
            cipher.setOrganizationUuid(orgId);
        } else {
            cipher.setUserUuid(userUuid);
        }
        applyData(cipher, cipherData, userUuid);
        cipherRepository.save(cipher);
        if (cipher.getOrganizationUuid() != null && collectionIds != null) {
            replaceCollections(cipher, collectionIds, userUuid);
        }
        return cipher;
    }

    @Transactional
    public Cipher update(Cipher cipher, Map<String, Object> data, String userUuid) {
        applyData(cipher, data, userUuid);
        return cipherRepository.save(cipher);
    }

    @Transactional
    public void softDelete(Cipher cipher) {
        cipher.setDeletedDate(Instant.now());
        cipherRepository.save(cipher);
    }

    @Transactional
    public Cipher restore(Cipher cipher) {
        cipher.setDeletedDate(null);
        return cipherRepository.save(cipher);
    }

    /** Hard delete: removes attachments (files + rows), favorites and collection links. */
    @Transactional
    public void delete(Cipher cipher) {
        for (var att : attachmentService.findByCipherUuid(cipher.getUuid())) {
            try {
                attachmentService.delete(cipher.getUuid(), att.getId());
            } catch (IOException ignored) {
                // DB row removal below is authoritative; stray files are harmless
            }
        }
        favoriteRepository.deleteByCipherUuid(cipher.getUuid());
        collectionCipherRepository.deleteByCipherUuid(cipher.getUuid());
        cipherRepository.delete(cipher);
    }

    /** Moves the given ciphers into a folder (or out of any folder when folderId is null). */
    @Transactional
    public void move(String userUuid, List<String> cipherIds, String folderId) {
        if (folderId != null) {
            Folder folder = folderRepository.findById(folderId)
                .filter(f -> userUuid.equals(f.getUserUuid()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid folder"));
            folderId = folder.getUuid();
        }
        for (String id : cipherIds) {
            Optional<Cipher> cipherOpt = findById(id).filter(c -> isAccessible(c, userUuid));
            if (cipherOpt.isEmpty()) continue;
            Cipher cipher = cipherOpt.get();
            cipher.setFolderUuid(folderId);
            cipherRepository.save(cipher);
        }
    }

    @Transactional
    public void setFavorite(String userUuid, String cipherUuid, boolean favorite) {
        boolean exists = favoriteRepository.existsByUserUuidAndCipherUuid(userUuid, cipherUuid);
        if (favorite && !exists) {
            favoriteRepository.save(new Favorite(userUuid, cipherUuid));
        } else if (!favorite && exists) {
            favoriteRepository.deleteByUserUuidAndCipherUuid(userUuid, cipherUuid);
        }
    }

    /** Per-user partial update (folder + favorite) usable on read-only shared ciphers. */
    @Transactional
    public Cipher updatePartial(String userUuid, Cipher cipher, String folderId, boolean favorite) {
        if (folderId != null) {
            folderRepository.findById(folderId)
                .filter(f -> userUuid.equals(f.getUserUuid()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid folder"));
        }
        cipher.setFolderUuid(folderId);
        cipherRepository.save(cipher);
        setFavorite(userUuid, cipher.getUuid(), favorite);
        return cipher;
    }

    /** Replaces the collections an org cipher is assigned to. */
    @Transactional
    public void replaceCollections(Cipher cipher, List<String> collectionIds, String userUuid) {
        String orgId = cipher.getOrganizationUuid();
        if (orgId == null) {
            throw new IllegalArgumentException("Cipher is not owned by an organization");
        }
        requireConfirmedMembership(userUuid, orgId);
        collectionCipherRepository.deleteByCipherUuid(cipher.getUuid());
        for (String collectionId : collectionIds) {
            collectionRepository.findById(collectionId)
                .filter(col -> orgId.equals(col.getOrgUuid()))
                .ifPresent(col -> {
                    CollectionCipher link = new CollectionCipher();
                    link.setCollectionUuid(col.getUuid());
                    link.setCipherUuid(cipher.getUuid());
                    collectionCipherRepository.save(link);
                });
        }
    }

    /** Bulk import: {folders, ciphers, folderRelationships[{key: cipherIdx, value: folderIdx}]}. */
    @Transactional
    public void importCiphers(String userUuid, List<Map<String, Object>> folders,
                              List<Map<String, Object>> ciphers,
                              List<Map<String, Object>> relationships) {
        List<String> folderIds = new ArrayList<>();
        if (folders != null) {
            for (Map<String, Object> f : folders) {
                Folder folder = new Folder();
                folder.setUuid(UuidUtil.newUuid());
                folder.setUserUuid(userUuid);
                folder.setName((String) f.get("name"));
                folderRepository.save(folder);
                folderIds.add(folder.getUuid());
            }
        }
        java.util.Map<Integer, Integer> cipherToFolder = new java.util.HashMap<>();
        if (relationships != null) {
            for (Map<String, Object> rel : relationships) {
                cipherToFolder.put(((Number) rel.get("key")).intValue(),
                    ((Number) rel.get("value")).intValue());
            }
        }
        if (ciphers != null) {
            for (int i = 0; i < ciphers.size(); i++) {
                Cipher cipher = create(userUuid, ciphers.get(i));
                Integer folderIdx = cipherToFolder.get(i);
                if (folderIdx != null && folderIdx >= 0 && folderIdx < folderIds.size()) {
                    cipher.setFolderUuid(folderIds.get(folderIdx));
                    cipherRepository.save(cipher);
                }
            }
        }
    }

    /** Deletes the user's entire personal vault (ciphers + folders). */
    @Transactional
    public void purge(String userUuid) {
        for (Cipher cipher : cipherRepository.findByUserUuid(userUuid)) {
            delete(cipher);
        }
        folderRepository.deleteAll(folderRepository.findByUserUuid(userUuid));
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

    /** P1 access model: owner, or confirmed member of the owning organization. */
    public boolean isAccessible(Cipher cipher, String userUuid) {
        if (userUuid.equals(cipher.getUserUuid())) return true;
        String orgId = cipher.getOrganizationUuid();
        return orgId != null && orgMembershipRepository.findByUserUuidAndOrgUuid(userUuid, orgId)
            .filter(m -> m.getStatus() == 2)
            .isPresent();
    }

    private void requireConfirmedMembership(String userUuid, String orgUuid) {
        orgMembershipRepository.findByUserUuidAndOrgUuid(userUuid, orgUuid)
            .filter(m -> m.getStatus() == 2)
            .orElseThrow(() -> new IllegalArgumentException(
                "You are not a confirmed member of this organization"));
    }

    private void applyData(Cipher cipher, Map<String, Object> data, String userUuid) {
        cipher.setType(((Number) data.getOrDefault("type", 1)).intValue());
        cipher.setName((String) data.get("name"));
        cipher.setNotes((String) data.get("notes"));
        cipher.setFolderUuid((String) data.get("folderId"));
        if (data.containsKey("key")) {
            cipher.setKey((String) data.get("key"));
        }
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
            if (data.containsKey("passwordHistory")) {
                cipher.setPasswordHistory(objectMapper.writeValueAsString(data.get("passwordHistory")));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cipher data", e);
        }
        if (data.get("favorite") instanceof Boolean favorite) {
            setFavorite(userUuid, cipher.getUuid(), favorite);
        }
    }
}
