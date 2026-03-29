package com.vaultguard.api.accounts;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.User;
import com.vaultguard.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {

    private final UserService userService;

    public AccountsController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String name = (String) body.getOrDefault("name", email);
        String masterPasswordHash = (String) body.get("masterPasswordHash");
        int kdf = ((Number) body.getOrDefault("kdf", 0)).intValue();
        int kdfIterations = ((Number) body.getOrDefault("kdfIterations", 600000)).intValue();
        Integer kdfMemory = body.containsKey("kdfMemory")
            ? ((Number) body.get("kdfMemory")).intValue() : null;
        Integer kdfParallelism = body.containsKey("kdfParallelism")
            ? ((Number) body.get("kdfParallelism")).intValue() : null;
        String key = (String) body.get("key");
        @SuppressWarnings("unchecked")
        Map<String, Object> keys = (Map<String, Object>) body.get("keys");
        String publicKey = keys != null ? (String) keys.get("publicKey") : null;
        String privateKey = keys != null ? (String) keys.get("encryptedPrivateKey") : null;

        userService.register(email, name, masterPasswordHash, kdf, kdfIterations,
            kdfMemory, kdfParallelism, key, privateKey, publicKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/prelogin")
    public ResponseEntity<Map<String, Object>> prelogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        return userService.findByEmail(email).map(user -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("Kdf", user.getKdfType());
            resp.put("KdfIterations", user.getKdfIterations());
            if (user.getKdfMemory() != null) resp.put("KdfMemory", user.getKdfMemory());
            if (user.getKdfParallelism() != null) resp.put("KdfParallelism", user.getKdfParallelism());
            return ResponseEntity.ok(resp);
        }).orElseGet(() -> {
            // Return defaults — do not leak whether account exists
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("Kdf", 0);
            resp.put("KdfIterations", 600000);
            return ResponseEntity.ok(resp);
        });
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> profile(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(toProfileResponse(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (body.containsKey("name")) user.setName((String) body.get("name"));
        if (body.containsKey("masterPasswordHint")) user.setPasswordHint((String) body.get("masterPasswordHint"));
        userService.save(user);
        return ResponseEntity.ok(toProfileResponse(user));
    }

    private Map<String, Object> toProfileResponse(User user) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", user.getUuid());
        resp.put("Name", user.getName());
        resp.put("Email", user.getEmail());
        resp.put("EmailVerified", user.getVerifiedAt() != null);
        resp.put("Premium", false);
        resp.put("MasterPasswordHint", user.getPasswordHint());
        resp.put("Culture", "en-US");
        resp.put("TwoFactorEnabled", false);
        resp.put("Key", user.getKeyHash());
        resp.put("PrivateKey", user.getPrivateKey());
        resp.put("SecurityStamp", user.getSecurityStamp());
        resp.put("Kdf", user.getKdfType());
        resp.put("KdfIterations", user.getKdfIterations());
        resp.put("KdfMemory", user.getKdfMemory());
        resp.put("KdfParallelism", user.getKdfParallelism());
        resp.put("Object", "profile");
        return resp;
    }
}
