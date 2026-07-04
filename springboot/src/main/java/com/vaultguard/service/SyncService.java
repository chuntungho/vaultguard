package com.vaultguard.service;

import com.vaultguard.db.entity.*;
import com.vaultguard.db.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SyncService {

    private final UserService userService;
    private final CipherService cipherService;
    private final FolderService folderService;
    private final CollectionRepository collectionRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final OrganizationRepository organizationRepository;
    private final CipherResponseMapper cipherResponseMapper;
    private final TwoFactorService twoFactorService;

    public SyncService(UserService userService, CipherService cipherService,
                       FolderService folderService, CollectionRepository collectionRepository,
                       OrgMembershipRepository orgMembershipRepository,
                       OrganizationRepository organizationRepository,
                       CipherResponseMapper cipherResponseMapper,
                       TwoFactorService twoFactorService) {
        this.userService = userService;
        this.cipherService = cipherService;
        this.folderService = folderService;
        this.collectionRepository = collectionRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.organizationRepository = organizationRepository;
        this.cipherResponseMapper = cipherResponseMapper;
        this.twoFactorService = twoFactorService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildSyncResponse(String userUuid) {
        User user = userService.findById(userUuid)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Cipher> ciphers = cipherService.findAllAccessibleByUser(userUuid);
        List<Folder> folders = folderService.findByUserUuid(userUuid);
        List<OrgMembership> confirmedMemberships = orgMembershipRepository.findByUserUuid(userUuid).stream()
            .filter(m -> m.getStatus() == 2)
            .toList();

        List<Map<String, Object>> cipherResponses = ciphers.stream()
            .map(c -> cipherResponseMapper.toResponse(c, userUuid)).toList();
        List<Map<String, Object>> folderResponses = folders.stream()
            .map(this::toFolderResponse).toList();
        List<Map<String, Object>> collectionResponses = confirmedMemberships.stream()
            .flatMap(m -> collectionRepository.findByOrgUuid(m.getOrgUuid()).stream())
            .map(this::toCollectionResponse)
            .toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Profile", toProfileResponse(user, confirmedMemberships));
        resp.put("Folders", folderResponses);
        resp.put("Collections", collectionResponses);
        resp.put("Ciphers", cipherResponses);
        resp.put("Domains", Map.of("EquivalentDomains", List.of(), "GlobalEquivalentDomains", List.of()));
        resp.put("Policies", List.of());
        resp.put("Sends", List.of());
        resp.put("Object", "sync");
        return resp;
    }

    private Map<String, Object> toProfileResponse(User user, List<OrgMembership> memberships) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("Id", user.getUuid());
        p.put("Name", user.getName());
        p.put("Email", user.getEmail());
        p.put("EmailVerified", user.getVerifiedAt() != null);
        p.put("Premium", false);
        p.put("MasterPasswordHint", user.getPasswordHint());
        p.put("Culture", "en-US");
        p.put("TwoFactorEnabled", twoFactorService.hasTwoFactor(user.getUuid()));
        p.put("Key", user.getKeyHash());
        p.put("PrivateKey", user.getPrivateKey());
        p.put("SecurityStamp", user.getSecurityStamp());
        p.put("Kdf", user.getKdfType());
        p.put("KdfIterations", user.getKdfIterations());
        p.put("KdfMemory", user.getKdfMemory());
        p.put("KdfParallelism", user.getKdfParallelism());
        p.put("Organizations", memberships.stream().map(m ->
            organizationRepository.findById(m.getOrgUuid())
                .map(org -> toOrgResponse(org, m)).orElse(null))
            .filter(o -> o != null).toList());
        p.put("Object", "profile");
        return p;
    }

    private Map<String, Object> toFolderResponse(Folder folder) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", folder.getUuid());
        resp.put("Name", folder.getName());
        resp.put("RevisionDate", folder.getUpdatedAt());
        resp.put("Object", "folder");
        return resp;
    }

    private Map<String, Object> toOrgResponse(Organization org, OrgMembership membership) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", org.getUuid());
        resp.put("Name", org.getName());
        resp.put("Type", membership.getAtype());
        resp.put("Status", membership.getStatus());
        resp.put("Enabled", true);
        resp.put("Key", membership.getAkey());
        resp.put("Object", "profileOrganization");
        return resp;
    }

    private Map<String, Object> toCollectionResponse(Collection collection) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", collection.getUuid());
        resp.put("OrganizationId", collection.getOrgUuid());
        resp.put("Name", collection.getName());
        resp.put("ReadOnly", false);
        resp.put("HidePasswords", false);
        resp.put("Object", "collectionDetails");
        return resp;
    }
}
