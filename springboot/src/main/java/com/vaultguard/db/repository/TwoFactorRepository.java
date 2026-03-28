package com.vaultguard.db.repository;

import com.vaultguard.db.entity.TwoFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TwoFactorRepository extends JpaRepository<TwoFactor, TwoFactor.TwoFactorId> {
    List<TwoFactor> findByUserUuidAndEnabled(String userUuid, boolean enabled);
    Optional<TwoFactor> findByUserUuidAndType(String userUuid, int type);
}
