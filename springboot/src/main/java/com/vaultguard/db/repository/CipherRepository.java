package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Cipher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CipherRepository extends JpaRepository<Cipher, String> {
    List<Cipher> findByUserUuid(String userUuid);
    List<Cipher> findByOrganizationUuid(String organizationUuid);
}
