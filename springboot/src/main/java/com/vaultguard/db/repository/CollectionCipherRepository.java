package com.vaultguard.db.repository;

import com.vaultguard.db.entity.CollectionCipher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface CollectionCipherRepository extends JpaRepository<CollectionCipher, CollectionCipher.CollectionCipherId> {
    List<CollectionCipher> findByCollectionUuid(String collectionUuid);
    List<CollectionCipher> findByCipherUuid(String cipherUuid);
    @Transactional
    void deleteByCipherUuid(String cipherUuid);
    @Transactional
    void deleteByCollectionUuid(String collectionUuid);
}
