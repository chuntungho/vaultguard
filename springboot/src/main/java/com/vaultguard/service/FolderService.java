package com.vaultguard.service;

import com.vaultguard.db.entity.Cipher;
import com.vaultguard.db.entity.Folder;
import com.vaultguard.db.repository.CipherRepository;
import com.vaultguard.db.repository.FolderRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final CipherRepository cipherRepository;

    public FolderService(FolderRepository folderRepository, CipherRepository cipherRepository) {
        this.folderRepository = folderRepository;
        this.cipherRepository = cipherRepository;
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

    /** Deleting a folder moves its ciphers out of the folder; ciphers are never deleted. */
    @Transactional
    public void delete(Folder folder) {
        for (Cipher cipher : cipherRepository.findByUserUuid(folder.getUserUuid())) {
            if (folder.getUuid().equals(cipher.getFolderUuid())) {
                cipher.setFolderUuid(null);
                cipherRepository.save(cipher);
            }
        }
        folderRepository.delete(folder);
    }

    public Optional<Folder> findById(String uuid) {
        return folderRepository.findById(uuid);
    }

    public List<Folder> findByUserUuid(String userUuid) {
        return folderRepository.findByUserUuid(userUuid);
    }
}
