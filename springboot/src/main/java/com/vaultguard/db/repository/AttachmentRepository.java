package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, String> {
    List<Attachment> findByCipherUuid(String cipherUuid);
    @Transactional
    void deleteByCipherUuid(String cipherUuid);

    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.cipherUuid IN " +
           "(SELECT c.uuid FROM Cipher c WHERE c.userUuid = :userUuid)")
    long countByUserUuid(@Param("userUuid") String userUuid);

    long countByCipherUuid(String cipherUuid);
}
