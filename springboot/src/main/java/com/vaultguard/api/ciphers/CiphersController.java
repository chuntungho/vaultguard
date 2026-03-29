package com.vaultguard.api.ciphers;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Cipher;
import com.vaultguard.service.CipherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ciphers")
public class CiphersController {

    private final CipherService cipherService;

    public CiphersController(CipherService cipherService) {
        this.cipherService = cipherService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        List<Cipher> ciphers = cipherService.findAllAccessibleByUser(principal.getUserUuid());
        List<Map<String, Object>> items = ciphers.stream().map(this::toCipherResponse).toList();
        return ResponseEntity.ok(Map.of("Data", items, "Object", "list"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(id)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(c -> ResponseEntity.ok(toCipherResponse(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        Cipher cipher = cipherService.create(principal.getUserUuid(), body);
        return ResponseEntity.ok(toCipherResponse(cipher));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(id)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(c -> ResponseEntity.ok(toCipherResponse(cipherService.update(c, body))))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        var found = cipherService.findById(id)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()));
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        cipherService.delete(found.get());
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toCipherResponse(Cipher cipher) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", cipher.getUuid());
        resp.put("Type", cipher.getType());
        resp.put("Name", cipher.getName());
        resp.put("Notes", cipher.getNotes());
        resp.put("FolderId", cipher.getFolderUuid());
        resp.put("OrganizationId", cipher.getOrganizationUuid());
        resp.put("Reprompt", cipher.getReprompt());
        resp.put("Data", cipher.getData());
        resp.put("Fields", cipher.getFields());
        resp.put("Key", cipher.getKey());
        resp.put("RevisionDate", cipher.getUpdatedAt());
        resp.put("CreationDate", cipher.getCreatedAt());
        resp.put("DeletedDate", cipher.getDeletedDate());
        resp.put("Object", "cipher");
        return resp;
    }
}
