package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, String> {
    List<Attachment> findByCipherUuid(String cipherUuid);
    @Transactional
    void deleteByCipherUuid(String cipherUuid);
}
