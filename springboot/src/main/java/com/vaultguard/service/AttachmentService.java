package com.vaultguard.service;

import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Attachment;
import com.vaultguard.db.repository.AttachmentRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final VaultGuardProperties props;

    public AttachmentService(AttachmentRepository attachmentRepository,
                              VaultGuardProperties props) {
        this.attachmentRepository = attachmentRepository;
        this.props = props;
    }

    @Transactional
    public Attachment store(String cipherUuid, MultipartFile file, String attachmentKey) throws IOException {
        String id = UuidUtil.newUuid();
        Path dir = Path.of(props.getAttachmentsPath(), cipherUuid);
        Files.createDirectories(dir);
        Path dest = dir.resolve(id);
        file.transferTo(dest);

        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setCipherUuid(cipherUuid);
        attachment.setFileName(file.getOriginalFilename() != null
            ? file.getOriginalFilename() : id);
        attachment.setFileSize(file.getSize());
        attachment.setAkey(attachmentKey);
        return attachmentRepository.save(attachment);
    }

    public Resource load(String cipherUuid, String attachmentId) {
        Path file = Path.of(props.getAttachmentsPath(), cipherUuid, attachmentId);
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) return null;
        return resource;
    }

    @Transactional
    public void delete(String cipherUuid, String attachmentId) throws IOException {
        attachmentRepository.deleteById(attachmentId);
        Path file = Path.of(props.getAttachmentsPath(), cipherUuid, attachmentId);
        Files.deleteIfExists(file);
    }

    public List<Attachment> findByCipherUuid(String cipherUuid) {
        return attachmentRepository.findByCipherUuid(cipherUuid);
    }

    public Optional<Attachment> findById(String id) {
        return attachmentRepository.findById(id);
    }
}
