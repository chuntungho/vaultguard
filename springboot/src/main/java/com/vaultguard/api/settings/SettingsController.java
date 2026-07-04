package com.vaultguard.api.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.User;
import com.vaultguard.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Equivalent-domains settings (REQ-SY-2). Global domain groups are not yet bundled. */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    public SettingsController(UserService userService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/domains")
    public ResponseEntity<Map<String, Object>> getDomains(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(toDomainsResponse(user));
    }

    @PostMapping("/domains")
    public ResponseEntity<Map<String, Object>> postDomains(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        try {
            Object equivalent = body.getOrDefault("equivalentDomains", List.of());
            Object excludedGlobals = body.getOrDefault("excludedGlobalEquivalentDomains", List.of());
            user.setEquivalentDomains(objectMapper.writeValueAsString(equivalent));
            user.setExcludedGlobals(objectMapper.writeValueAsString(excludedGlobals));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        userService.save(user);
        return ResponseEntity.ok(toDomainsResponse(user));
    }

    @PutMapping("/domains")
    public ResponseEntity<Map<String, Object>> putDomains(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        return postDomains(principal, body);
    }

    private Map<String, Object> toDomainsResponse(User user) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("EquivalentDomains", parseJson(user.getEquivalentDomains()));
        resp.put("GlobalEquivalentDomains", List.of());
        resp.put("Object", "domains");
        return resp;
    }

    private Object parseJson(String json) {
        try {
            return json != null ? objectMapper.readValue(json, Object.class) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
