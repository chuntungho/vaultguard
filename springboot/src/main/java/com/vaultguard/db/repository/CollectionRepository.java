package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CollectionRepository extends JpaRepository<Collection, String> {
    List<Collection> findByOrgUuid(String orgUuid);
}
