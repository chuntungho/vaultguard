package com.vaultguard.api.ciphers;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Attachment;
import com.vaultguard.service.AttachmentService;
import com.vaultguard.service.CipherService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ciphers/{cipherId}/attachment")
public class AttachmentsController {

    private final CipherService cipherService;
    private final AttachmentService attachmentService;
    private final VaultGuardProperties props;

    public AttachmentsController(CipherService cipherService, AttachmentService attachmentService,
                                  VaultGuardProperties props) {
        this.cipherService = cipherService;
        this.attachmentService = attachmentService;
        this.props = props;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(
        @PathVariable String cipherId,
        @RequestParam("data") MultipartFile file,
        @RequestParam(value = "key", required = false) String attachmentKey,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(cipherId)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(cipher -> {
                try {
                    Attachment att = attachmentService.store(cipherId, file, attachmentKey);
                    return ResponseEntity.ok(toAttachmentResponse(att, cipherId));
                } catch (Exception e) {
                    throw new RuntimeException("Upload failed", e);
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(
        @PathVariable String cipherId,
        @PathVariable String attachmentId,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(cipherId)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .<ResponseEntity<Resource>>map(cipher -> {
                Resource resource = attachmentService.load(cipherId, attachmentId);
                if (resource == null) return ResponseEntity.<Resource>notFound().build();
                return attachmentService.findById(attachmentId)
                    .filter(att -> att.getCipherUuid().equals(cipherId))
                    .map(att -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + att.getFileName() + "\"")
                        .body(resource))
                    .orElse(ResponseEntity.<Resource>notFound().build());
            })
            .orElse(ResponseEntity.<Resource>notFound().build());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
        @PathVariable String cipherId,
        @PathVariable String attachmentId,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(cipherId)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .<ResponseEntity<Void>>map(cipher -> {
                // Verify the attachment actually belongs to this cipher
                var attachment = attachmentService.findById(attachmentId);
                if (attachment.isEmpty() || !attachment.get().getCipherUuid().equals(cipherId)) {
                    return ResponseEntity.<Void>notFound().build();
                }
                try {
                    attachmentService.delete(cipherId, attachmentId);
                    return ResponseEntity.<Void>noContent().build();
                } catch (Exception e) {
                    throw new RuntimeException("Delete failed", e);
                }
            })
            .orElse(ResponseEntity.<Void>notFound().build());
    }

    private Map<String, Object> toAttachmentResponse(Attachment att, String cipherId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", att.getId());
        resp.put("FileName", att.getFileName());
        resp.put("Size", att.getFileSize());
        resp.put("SizeName", humanSize(att.getFileSize()));
        resp.put("Key", att.getAkey());
        resp.put("Url", props.getDomain() + "/api/ciphers/" + cipherId + "/attachment/" + att.getId());
        resp.put("Object", "attachment");
        return resp;
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}
