package com.vaultguard.service;

import com.vaultguard.db.entity.Collection;
import com.vaultguard.db.entity.OrgMembership;
import com.vaultguard.db.entity.Organization;
import com.vaultguard.db.repository.*;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final CollectionRepository collectionRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OrgMembershipRepository orgMembershipRepository,
                                CollectionRepository collectionRepository) {
        this.organizationRepository = organizationRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.collectionRepository = collectionRepository;
    }

    @Transactional
    public Organization create(String ownerUserUuid, String name, String billingEmail,
                                String ownerKey) {
        Organization org = new Organization();
        org.setUuid(UuidUtil.newUuid());
        org.setName(name);
        org.setBillingEmail(billingEmail);
        organizationRepository.save(org);

        OrgMembership membership = new OrgMembership();
        membership.setUuid(UuidUtil.newUuid());
        membership.setUserUuid(ownerUserUuid);
        membership.setOrgUuid(org.getUuid());
        membership.setAtype(0); // Owner
        membership.setStatus(2); // Confirmed
        membership.setAccessAll(true);
        membership.setAkey(ownerKey);
        orgMembershipRepository.save(membership);

        return org;
    }

    public Optional<Organization> findById(String uuid) {
        return organizationRepository.findById(uuid);
    }

    public Optional<OrgMembership> findMembership(String userUuid, String orgUuid) {
        return orgMembershipRepository.findByUserUuidAndOrgUuid(userUuid, orgUuid);
    }

    @Transactional
    public Collection createCollection(String orgUuid, String name) {
        Collection col = new Collection();
        col.setUuid(UuidUtil.newUuid());
        col.setOrgUuid(orgUuid);
        col.setName(name);
        return collectionRepository.save(col);
    }

    public List<Collection> findCollections(String orgUuid) {
        return collectionRepository.findByOrgUuid(orgUuid);
    }
}
