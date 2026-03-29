package com.vaultguard.api.folders;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Folder;
import com.vaultguard.service.FolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FoldersController {

    private final FolderService folderService;

    public FoldersController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        List<Folder> folders = folderService.findByUserUuid(principal.getUserUuid());
        List<Map<String, Object>> items = folders.stream().map(this::toFolderResponse).toList();
        return ResponseEntity.ok(Map.of("Data", items, "Object", "list"));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        Folder folder = folderService.create(principal.getUserUuid(), body.get("name"));
        return ResponseEntity.ok(toFolderResponse(folder));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
        @PathVariable String id,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return folderService.findById(id)
            .filter(f -> principal.getUserUuid().equals(f.getUserUuid()))
            .map(f -> ResponseEntity.ok(toFolderResponse(folderService.update(f, body.get("name")))))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        var found = folderService.findById(id)
            .filter(f -> principal.getUserUuid().equals(f.getUserUuid()));
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        folderService.delete(found.get());
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toFolderResponse(Folder folder) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", folder.getUuid());
        resp.put("Name", folder.getName());
        resp.put("RevisionDate", folder.getUpdatedAt());
        resp.put("Object", "folder");
        return resp;
    }
}
