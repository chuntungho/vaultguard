package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Cipher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CipherRepository extends JpaRepository<Cipher, String> {
    List<Cipher> findByUserUuid(String userUuid);
    List<Cipher> findByOrganizationUuid(String organizationUuid);

    // Full query (including org-accessible ciphers) is added in Task 5 once OrgMembership entity exists.
    // Placeholder: returns only user-owned ciphers until then.
    default List<Cipher> findAllAccessibleByUser(String userUuid) {
        return findByUserUuid(userUuid);
    }
}
