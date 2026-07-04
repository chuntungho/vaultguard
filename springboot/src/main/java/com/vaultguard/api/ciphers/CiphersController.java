package com.vaultguard.api.ciphers;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Cipher;
import com.vaultguard.service.CipherResponseMapper;
import com.vaultguard.service.CipherService;
import com.vaultguard.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@RestController
@RequestMapping("/api/ciphers")
public class CiphersController {

    private final CipherService cipherService;
    private final CipherResponseMapper mapper;
    private final UserService userService;

    public CiphersController(CipherService cipherService, CipherResponseMapper mapper,
                             UserService userService) {
        this.cipherService = cipherService;
        this.mapper = mapper;
        this.userService = userService;
    }

    // ---- Read ----

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        List<Cipher> ciphers = cipherService.findAllAccessibleByUser(principal.getUserUuid());
        List<Map<String, Object>> items = ciphers.stream()
            .map(c -> mapper.toResponse(c, principal.getUserUuid())).toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Data", items);
        resp.put("ContinuationToken", null);
        resp.put("Object", "list");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return withAccessibleCipher(id, principal,
            c -> ResponseEntity.ok(mapper.toResponse(c, principal.getUserUuid())));
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<Map<String, Object>> getDetails(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return get(id, principal);
    }

    @GetMapping("/{id}/admin")
    public ResponseEntity<Map<String, Object>> getAdmin(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return get(id, principal);
    }

    // ---- Create / update ----

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        Cipher cipher = cipherService.create(principal.getUserUuid(), body);
        return ResponseEntity.ok(mapper.toResponse(cipher, principal.getUserUuid()));
    }

    /** {cipher, collectionIds} form; required for organization-owned ciphers. */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createWithCollections(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cipherData = body.containsKey("cipher")
            ? (Map<String, Object>) body.get("cipher") : body;
        @SuppressWarnings("unchecked")
        List<String> collectionIds = (List<String>) body.get("collectionIds");
        Cipher cipher = cipherService.createWithCollections(
            principal.getUserUuid(), cipherData, collectionIds);
        return ResponseEntity.ok(mapper.toResponse(cipher, principal.getUserUuid()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return withAccessibleCipher(id, principal, c -> ResponseEntity.ok(
            mapper.toResponse(cipherService.update(c, body, principal.getUserUuid()),
                principal.getUserUuid())));
    }

    @PostMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePost(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return update(id, body, principal);
    }

    @PutMapping("/{id}/admin")
    public ResponseEntity<Map<String, Object>> updateAdmin(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return update(id, body, principal);
    }

    @PostMapping("/{id}/admin")
    public ResponseEntity<Map<String, Object>> updateAdminPost(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return update(id, body, principal);
    }

    /** Per-user partial update (folderId + favorite); works on read-only ciphers. */
    @PutMapping("/{id}/partial")
    public ResponseEntity<Map<String, Object>> partial(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return withAccessibleCipher(id, principal, c -> {
            String folderId = (String) body.get("folderId");
            boolean favorite = Boolean.TRUE.equals(body.get("favorite"));
            Cipher updated = cipherService.updatePartial(principal.getUserUuid(), c, folderId, favorite);
            return ResponseEntity.ok(mapper.toResponse(updated, principal.getUserUuid()));
        });
    }

    @PostMapping("/{id}/partial")
    public ResponseEntity<Map<String, Object>> partialPost(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return partial(id, body, principal);
    }

    // ---- Collections assignment ----

    @PutMapping("/{id}/collections")
    public ResponseEntity<Map<String, Object>> updateCollections(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return withAccessibleCipher(id, principal, c -> {
            @SuppressWarnings("unchecked")
            List<String> collectionIds = (List<String>) body.get("collectionIds");
            cipherService.replaceCollections(c, collectionIds != null ? collectionIds : List.of(),
                principal.getUserUuid());
            return ResponseEntity.ok(mapper.toResponse(c, principal.getUserUuid()));
        });
    }

    @PostMapping("/{id}/collections")
    public ResponseEntity<Map<String, Object>> updateCollectionsPost(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return updateCollections(id, body, principal);
    }

    @PutMapping("/{id}/collections-admin")
    public ResponseEntity<Map<String, Object>> updateCollectionsAdmin(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return updateCollections(id, body, principal);
    }

    @PostMapping("/{id}/collections-admin")
    public ResponseEntity<Map<String, Object>> updateCollectionsAdminPost(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return updateCollections(id, body, principal);
    }

    // ---- Move ----

    @PutMapping("/move")
    public ResponseEntity<Void> move(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        String folderId = (String) body.get("folderId");
        if (ids == null) return ResponseEntity.badRequest().build();
        cipherService.move(principal.getUserUuid(), ids, folderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/move")
    public ResponseEntity<Void> movePost(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return move(body, principal);
    }

    // ---- Delete (soft = trash) / restore ----

    @PutMapping("/{id}/delete")
    public ResponseEntity<Void> softDelete(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return withAccessibleCipher(id, principal, c -> {
            cipherService.softDelete(c);
            return ResponseEntity.<Void>ok().build();
        });
    }

    @PutMapping("/{id}/delete-admin")
    public ResponseEntity<Void> softDeleteAdmin(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return softDelete(id, principal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hardDelete(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return withAccessibleCipher(id, principal, c -> {
            cipherService.delete(c);
            return ResponseEntity.<Void>ok().build();
        });
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> hardDeletePost(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return hardDelete(id, principal);
    }

    @DeleteMapping("/{id}/admin")
    public ResponseEntity<Void> hardDeleteAdmin(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return hardDelete(id, principal);
    }

    @PostMapping("/{id}/delete-admin")
    public ResponseEntity<Void> hardDeleteAdminPost(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return hardDelete(id, principal);
    }

    @PutMapping("/delete")
    public ResponseEntity<Void> softDeleteMany(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return forEachOwned(body, principal, c -> {
            cipherService.softDelete(c);
            return null;
        });
    }

    @DeleteMapping
    public ResponseEntity<Void> hardDeleteMany(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return forEachOwned(body, principal, c -> {
            cipherService.delete(c);
            return null;
        });
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> hardDeleteManyPost(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return hardDeleteMany(body, principal);
    }

    @PutMapping("/delete-admin")
    public ResponseEntity<Void> softDeleteManyAdmin(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return softDeleteMany(body, principal);
    }

    @DeleteMapping("/admin")
    public ResponseEntity<Void> hardDeleteManyAdmin(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return hardDeleteMany(body, principal);
    }

    @PostMapping("/delete-admin")
    public ResponseEntity<Void> hardDeleteManyAdminPost(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return hardDeleteMany(body, principal);
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return withAccessibleCipher(id, principal, c -> ResponseEntity.ok(
            mapper.toResponse(cipherService.restore(c), principal.getUserUuid())));
    }

    @PutMapping("/{id}/restore-admin")
    public ResponseEntity<Map<String, Object>> restoreAdmin(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return restore(id, principal);
    }

    @PutMapping("/restore")
    public ResponseEntity<Map<String, Object>> restoreMany(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        if (ids == null) return ResponseEntity.badRequest().build();
        List<Map<String, Object>> restored = ids.stream()
            .map(id -> cipherService.findById(id)
                .filter(c -> cipherService.isAccessible(c, principal.getUserUuid()))
                .map(c -> mapper.toResponse(cipherService.restore(c), principal.getUserUuid()))
                .orElse(null))
            .filter(java.util.Objects::nonNull)
            .toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Data", restored);
        resp.put("ContinuationToken", null);
        resp.put("Object", "list");
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/restore-admin")
    public ResponseEntity<Map<String, Object>> restoreManyAdmin(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return restoreMany(body, principal);
    }

    // ---- Import / purge ----

    @PostMapping("/import")
    public ResponseEntity<Void> importCiphers(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> folders = (List<Map<String, Object>>) body.get("folders");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ciphers = (List<Map<String, Object>>) body.get("ciphers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relationships =
            (List<Map<String, Object>>) body.get("folderRelationships");
        cipherService.importCiphers(principal.getUserUuid(), folders, ciphers, relationships);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/purge")
    public ResponseEntity<Map<String, Object>> purge(
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        var user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String hash = body.get("masterPasswordHash");
        if (hash == null || !userService.verifyPassword(hash, user)) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Invalid password", "Object", "error"));
        }
        cipherService.purge(principal.getUserUuid());
        return ResponseEntity.ok().build();
    }

    // ---- Helpers ----

    private <T> ResponseEntity<T> withAccessibleCipher(
        String id, VaultGuardUserDetails principal,
        Function<Cipher, ResponseEntity<T>> action) {
        Optional<Cipher> found = cipherService.findById(id)
            .filter(c -> cipherService.isAccessible(c, principal.getUserUuid()));
        return found.map(action).orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<Void> forEachOwned(Map<String, Object> body,
                                              VaultGuardUserDetails principal,
                                              Function<Cipher, Void> action) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        if (ids == null) return ResponseEntity.badRequest().build();
        for (String id : ids) {
            cipherService.findById(id)
                .filter(c -> cipherService.isAccessible(c, principal.getUserUuid()))
                .ifPresent(action::apply);
        }
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(Map.of("message", e.getMessage(), "Object", "error"));
    }
}
