package com.vaultguard.service;

import com.vaultguard.db.entity.Folder;
import com.vaultguard.db.repository.FolderRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FolderService {

    private final FolderRepository folderRepository;

    public FolderService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Transactional
    public Folder create(String userUuid, String name) {
        Folder folder = new Folder();
        folder.setUuid(UuidUtil.newUuid());
        folder.setUserUuid(userUuid);
        folder.setName(name);
        return folderRepository.save(folder);
    }

    @Transactional
    public Folder update(Folder folder, String name) {
        folder.setName(name);
        return folderRepository.save(folder);
    }

    @Transactional
    public void delete(Folder folder) {
        folderRepository.delete(folder);
    }

    public Optional<Folder> findById(String uuid) {
        return folderRepository.findById(uuid);
    }

    public List<Folder> findByUserUuid(String userUuid) {
        return folderRepository.findByUserUuid(userUuid);
    }
}
