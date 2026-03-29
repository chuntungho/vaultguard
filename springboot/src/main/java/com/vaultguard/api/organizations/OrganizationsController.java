package com.vaultguard.api.organizations;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Organization;
import com.vaultguard.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationsController {

    private final OrganizationService organizationService;

    public OrganizationsController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String billingEmail = (String) body.getOrDefault("billingEmail", "");
        String key = (String) body.get("key");
        Organization org = organizationService.create(principal.getUserUuid(), name, billingEmail, key);
        return ResponseEntity.ok(toOrgResponse(org));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return organizationService.findMembership(principal.getUserUuid(), id)
            .flatMap(m -> organizationService.findById(id))
            .map(org -> ResponseEntity.ok(toOrgResponse(org)))
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toOrgResponse(Organization org) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", org.getUuid());
        resp.put("Name", org.getName());
        resp.put("BillingEmail", org.getBillingEmail());
        resp.put("Plan", org.getPlan());
        resp.put("Object", "organization");
        return resp;
    }
}
