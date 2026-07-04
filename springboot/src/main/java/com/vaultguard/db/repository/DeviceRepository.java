package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, String> {
    List<Device> findByUserUuid(String userUuid);
    Optional<Device> findByUserUuidAndName(String userUuid, String name);
    Optional<Device> findByUuidAndUserUuid(String uuid, String userUuid);
    Optional<Device> findByRefreshToken(String refreshToken);
    boolean existsByUuid(String uuid);
    @Transactional
    void deleteByUserUuid(String userUuid);
}
