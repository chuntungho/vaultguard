package com.vaultguard.service;

import com.vaultguard.db.entity.*;
import com.vaultguard.db.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final CipherRepository cipherRepository;
    private final AttachmentRepository attachmentRepository;
    private final DeviceRepository deviceRepository;
    private final TwoFactorRepository twoFactorRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final CollectionRepository collectionRepository;

    public AdminService(UserRepository userRepository,
                        CipherRepository cipherRepository,
                        AttachmentRepository attachmentRepository,
                        DeviceRepository deviceRepository,
                        TwoFactorRepository twoFactorRepository,
                        OrganizationRepository organizationRepository,
                        OrgMembershipRepository orgMembershipRepository,
                        CollectionRepository collectionRepository) {
        this.userRepository = userRepository;
        this.cipherRepository = cipherRepository;
        this.attachmentRepository = attachmentRepository;
        this.deviceRepository = deviceRepository;
        this.twoFactorRepository = twoFactorRepository;
        this.organizationRepository = organizationRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.collectionRepository = collectionRepository;
    }

    public List<Map<String, Object>> listUsers() {
        return userRepository.findAll().stream().map(this::toUserSummary).toList();
    }

    private Map<String, Object> toUserSummary(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getUuid());
        m.put("name", user.getName());
        m.put("email", user.getEmail());
        m.put("enabled", user.isEnabled());
        m.put("emailVerified", user.getVerifiedAt() != null);
        m.put("twoFactorEnabled", !twoFactorRepository.findByUserUuidAndEnabled(user.getUuid(), true).isEmpty());
        m.put("createdAt", user.getCreatedAt());
        m.put("cipherCount", cipherRepository.countByUserUuid(user.getUuid()));
        m.put("attachmentCount", attachmentRepository.countByUserUuid(user.getUuid()));
        List<Map<String, String>> orgs = orgMembershipRepository.findByUserUuid(user.getUuid()).stream()
            .filter(mem -> mem.getStatus() == 2)
            .map(mem -> organizationRepository.findById(mem.getOrgUuid())
                .map(org -> Map.of("id", org.getUuid(), "name", org.getName()))
                .orElse(null))
            .filter(Objects::nonNull)
            .toList();
        m.put("organizations", orgs);
        return m;
    }

    @Transactional
    public void deleteUser(String uuid) {
        deviceRepository.deleteByUserUuid(uuid);
        twoFactorRepository.deleteAll(twoFactorRepository.findByUserUuidAndEnabled(uuid, true));
        cipherRepository.deleteAll(cipherRepository.findByUserUuid(uuid));
        userRepository.deleteById(uuid);
    }

    @Transactional
    public Map<String, Object> setUserEnabled(String uuid, boolean enabled) {
        User user = userRepository.findById(uuid)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(enabled);
        userRepository.save(user);
        return Map.of("id", user.getUuid(), "email", user.getEmail(), "enabled", user.isEnabled());
    }

    @Transactional
    public void deauthUser(String uuid) {
        deviceRepository.deleteByUserUuid(uuid);
    }

    @Transactional
    public void remove2fa(String uuid) {
        twoFactorRepository.deleteAll(
            twoFactorRepository.findByUserUuidAndEnabled(uuid, true));
    }

    public List<Map<String, Object>> listOrganizations() {
        return organizationRepository.findAll().stream().map(org -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", org.getUuid());
            m.put("name", org.getName());
            m.put("billingEmail", org.getBillingEmail());
            m.put("userCount", orgMembershipRepository.countByOrgUuid(org.getUuid()));
            m.put("cipherCount", cipherRepository.countByOrganizationUuid(org.getUuid()));
            m.put("collectionCount", collectionRepository.countByOrgUuid(org.getUuid()));
            return m;
        }).toList();
    }

    @Transactional
    public void deleteOrganization(String uuid) {
        cipherRepository.deleteAll(cipherRepository.findByOrganizationUuid(uuid));
        collectionRepository.deleteAll(collectionRepository.findByOrgUuid(uuid));
        orgMembershipRepository.deleteByOrgUuid(uuid);
        organizationRepository.deleteById(uuid);
    }
}
