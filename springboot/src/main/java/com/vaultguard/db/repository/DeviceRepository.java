package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, String> {
    List<Device> findByUserUuid(String userUuid);
    Optional<Device> findByUserUuidAndName(String userUuid, String name);
    void deleteByUserUuid(String userUuid);
}
