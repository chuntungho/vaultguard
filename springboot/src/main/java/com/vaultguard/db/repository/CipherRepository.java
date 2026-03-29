package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Cipher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CipherRepository extends JpaRepository<Cipher, String> {
    List<Cipher> findByUserUuid(String userUuid);
    List<Cipher> findByOrganizationUuid(String organizationUuid);

    @Query("SELECT c FROM Cipher c WHERE c.userUuid = :userUuid OR c.organizationUuid IN " +
           "(SELECT m.orgUuid FROM OrgMembership m WHERE m.userUuid = :userUuid AND m.status = 2)")
    List<Cipher> findAllAccessibleByUser(@Param("userUuid") String userUuid);
}
