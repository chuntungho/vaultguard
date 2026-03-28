package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, String> {
    List<Folder> findByUserUuid(String userUuid);
}
