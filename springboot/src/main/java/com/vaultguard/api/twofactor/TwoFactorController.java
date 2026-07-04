package com.vaultguard.api.twofactor;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.TwoFactor;
import com.vaultguard.db.entity.User;
import com.vaultguard.service.TwoFactorService;
import com.vaultguard.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/two-factor")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;
    private final UserService userService;

    public TwoFactorController(TwoFactorService twoFactorService, UserService userService) {
        this.twoFactorService = twoFactorService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        List<Map<String, Object>> items = twoFactorService.getProviders(principal.getUserUuid())
            .stream().map(tf -> {
                Map<String, Object> p = new LinkedHashMap<String, Object>();
                p.put("enabled", tf.isEnabled());
                p.put("type", tf.getType());
                p.put("object", "twoFactorProvider");
                return p;
            }).toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("data", items);
        resp.put("object", "list");
        resp.put("continuationToken", null);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/get-recover")
    public ResponseEntity<Map<String, Object>> getRecover(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, String> body) {
        User user = requirePassword(principal, body);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", user.getTotpRecover());
        resp.put("object", "twoFactorRecover");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disable(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        User user = requirePassword(principal, stringMap(body));
        int type = Integer.parseInt(String.valueOf(body.get("type")));
        twoFactorService.disable(user.getUuid(), type);
        return ResponseEntity.ok(Map.of(
            "enabled", false,
            "type", type,
            "object", "twoFactorProvider"));
    }

    @PutMapping("/disable")
    public ResponseEntity<Map<String, Object>> disablePut(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        return disable(principal, body);
    }

    @PostMapping("/get-authenticator")
    public ResponseEntity<Map<String, Object>> getAuthenticator(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, String> body) {
        User user = requirePassword(principal, body);
        var existing = twoFactorService.totpSecret(user.getUuid());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("enabled", existing.isPresent());
        // 20 random bytes → 32 base32 chars, same as the Rust implementation
        resp.put("key", existing.orElseGet(twoFactorService::generateTotpSecret));
        resp.put("object", "twoFactorAuthenticator");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/authenticator")
    public ResponseEntity<Map<String, Object>> activateAuthenticator(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        User user = requirePassword(principal, stringMap(body));
        String key = (String) body.get("key");
        String token = String.valueOf(body.get("token"));
        if (key == null || key.length() != 32) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Invalid totp secret", "Object", "error"));
        }
        if (!twoFactorService.verifyTotp(key.toUpperCase(), token)) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Invalid TOTP code", "Object", "error"));
        }
        twoFactorService.enableTotp(user.getUuid(), key.toUpperCase());
        return ResponseEntity.ok(Map.of(
            "enabled", true,
            "key", key,
            "object", "twoFactorAuthenticator"));
    }

    @PutMapping("/authenticator")
    public ResponseEntity<Map<String, Object>> activateAuthenticatorPut(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        return activateAuthenticator(principal, body);
    }

    @DeleteMapping("/authenticator")
    public ResponseEntity<Map<String, Object>> disableAuthenticator(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        User user = requirePassword(principal, stringMap(body));
        twoFactorService.disable(user.getUuid(), TwoFactorService.TYPE_TOTP);
        return ResponseEntity.ok(Map.of(
            "enabled", false,
            "type", TwoFactorService.TYPE_TOTP,
            "object", "twoFactorProvider"));
    }

    @PostMapping("/get-email")
    public ResponseEntity<Map<String, Object>> getEmail(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, String> body) {
        User user = requirePassword(principal, body);
        var email = twoFactorService.emailConfig(user.getUuid());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("email", email.orElse(null));
        resp.put("enabled", email.isPresent());
        resp.put("object", "twoFactorEmail");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/send-email")
    public ResponseEntity<Void> sendEmail(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, String> body) {
        User user = requirePassword(principal, body);
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        twoFactorService.startEmailSetup(user.getUuid(), email.toLowerCase());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/email")
    public ResponseEntity<Map<String, Object>> activateEmail(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, String> body) {
        User user = requirePassword(principal, body);
        String email = body.get("email");
        String token = body.get("token");
        if (email == null || token == null
            || !twoFactorService.activateEmail(user.getUuid(), email.toLowerCase(), token)) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Token is invalid", "Object", "error"));
        }
        return ResponseEntity.ok(Map.of(
            "email", email.toLowerCase(),
            "enabled", true,
            "object", "twoFactorEmail"));
    }

    /** Unauthenticated: sends the email login token during the 2FA login step. */
    @PostMapping("/send-email-login")
    public ResponseEntity<Map<String, Object>> sendEmailLogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String masterPasswordHash = body.get("masterPasswordHash");
        if (email == null || masterPasswordHash == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "No password hash has been submitted.", "Object", "error"));
        }
        User user = userService.findByEmail(email).orElse(null);
        if (user == null || !userService.verifyPassword(masterPasswordHash, user)) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Username or password is incorrect. Try again.", "Object", "error"));
        }
        try {
            twoFactorService.sendEmailLoginCode(user.getUuid());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", e.getMessage(), "Object", "error"));
        }
        return ResponseEntity.ok().build();
    }

    private User requirePassword(VaultGuardUserDetails principal, Map<String, String> body) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String hash = body.get("masterPasswordHash");
        if (hash == null || !userService.verifyPassword(hash, user)) {
            throw new IllegalArgumentException("Invalid password");
        }
        return user;
    }

    private static Map<String, String> stringMap(Map<String, Object> body) {
        Map<String, String> out = new LinkedHashMap<>();
        body.forEach((k, v) -> out.put(k, v != null ? String.valueOf(v) : null));
        return out;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(Map.of("message", e.getMessage(), "Object", "error"));
    }
}
