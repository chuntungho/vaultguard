package com.vaultguard.api.collections;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Collection;
import com.vaultguard.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/collections")
public class CollectionsController {

    private final OrganizationService organizationService;

    public CollectionsController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @PathVariable String orgId,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        if (organizationService.findMembership(principal.getUserUuid(), orgId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> items = organizationService.findCollections(orgId)
            .stream().map(this::toCollectionResponse).toList();
        return ResponseEntity.ok(Map.of("Data", items, "Object", "list"));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @PathVariable String orgId,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        if (organizationService.findMembership(principal.getUserUuid(), orgId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Collection col = organizationService.createCollection(orgId, body.get("name"));
        return ResponseEntity.ok(toCollectionResponse(col));
    }

    private Map<String, Object> toCollectionResponse(Collection collection) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", collection.getUuid());
        resp.put("OrganizationId", collection.getOrgUuid());
        resp.put("Name", collection.getName());
        resp.put("Object", "collection");
        return resp;
    }
}
