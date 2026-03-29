package com.vaultguard.api.sync;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return ResponseEntity.ok(syncService.buildSyncResponse(principal.getUserUuid()));
    }
}
