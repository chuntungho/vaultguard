package com.vaultguard.api.admin;

import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final VaultGuardProperties props;

    public AdminController(AdminService adminService, VaultGuardProperties props) {
        this.adminService = adminService;
        this.props = props;
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @DeleteMapping("/users/{uuid}")
    public ResponseEntity<Void> deleteUser(@PathVariable String uuid) {
        try {
            adminService.deleteUser(uuid);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/users/{uuid}/disable")
    public ResponseEntity<Map<String, Object>> disableUser(@PathVariable String uuid) {
        try {
            return ResponseEntity.ok(adminService.setUserEnabled(uuid, false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/users/{uuid}/enable")
    public ResponseEntity<Map<String, Object>> enableUser(@PathVariable String uuid) {
        try {
            return ResponseEntity.ok(adminService.setUserEnabled(uuid, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/users/{uuid}/deauth")
    public ResponseEntity<Void> deauthUser(@PathVariable String uuid) {
        adminService.deauthUser(uuid);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{uuid}/2fa")
    public ResponseEntity<Void> remove2fa(@PathVariable String uuid) {
        adminService.remove2fa(uuid);
        return ResponseEntity.noContent().build();
    }

    // ── Organizations ────────────────────────────────────────────────────────

    @GetMapping("/organizations")
    public ResponseEntity<List<Map<String, Object>>> listOrganizations() {
        return ResponseEntity.ok(adminService.listOrganizations());
    }

    @DeleteMapping("/organizations/{uuid}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable String uuid) {
        try {
            adminService.deleteOrganization(uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("domain", props.getDomain());
        s.put("signupsAllowed", props.isSignupsAllowed());
        s.put("invitationsAllowed", props.isInvitationsAllowed());
        s.put("passwordIterations", props.getPasswordIterations());
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("from", props.getMail().getFrom());
        mail.put("fromName", props.getMail().getFromName());
        s.put("mail", mail);
        return ResponseEntity.ok(s);
    }

    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestBody Map<String, Object> body) {
        if (body.containsKey("domain")) props.setDomain((String) body.get("domain"));
        if (body.containsKey("signupsAllowed"))
            props.setSignupsAllowed(Boolean.TRUE.equals(body.get("signupsAllowed")));
        if (body.containsKey("invitationsAllowed"))
            props.setInvitationsAllowed(Boolean.TRUE.equals(body.get("invitationsAllowed")));
        return getSettings();
    }

    // ── Diagnostics ──────────────────────────────────────────────────────────

    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("version", "1.0.0");
        d.put("javaVersion", System.getProperty("java.version"));
        d.put("javaVendor", System.getProperty("java.vendor"));
        d.put("osName", System.getProperty("os.name"));
        d.put("osArch", System.getProperty("os.arch"));
        d.put("serverTime", Instant.now().toString());
        d.put("domain", props.getDomain());
        return ResponseEntity.ok(d);
    }
}
