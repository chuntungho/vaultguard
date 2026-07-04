package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Favorite.FavoriteId> {
    List<Favorite> findByUserUuid(String userUuid);
    boolean existsByUserUuidAndCipherUuid(String userUuid, String cipherUuid);
    @Transactional
    void deleteByUserUuidAndCipherUuid(String userUuid, String cipherUuid);
    @Transactional
    void deleteByCipherUuid(String cipherUuid);
}
