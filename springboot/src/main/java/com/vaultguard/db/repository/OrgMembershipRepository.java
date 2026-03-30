package com.vaultguard.db.repository;

import com.vaultguard.db.entity.OrgMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface OrgMembershipRepository extends JpaRepository<OrgMembership, String> {
    List<OrgMembership> findByUserUuid(String userUuid);
    List<OrgMembership> findByOrgUuid(String orgUuid);
    Optional<OrgMembership> findByUserUuidAndOrgUuid(String userUuid, String orgUuid);
    List<OrgMembership> findByUserUuidAndStatus(String userUuid, int status);
    long countByOrgUuid(String orgUuid);
    @Transactional
    void deleteByOrgUuid(String orgUuid);
}
