package com.vaultguard.api.accounts;

import com.vaultguard.auth.JwtService;
import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.User;
import com.vaultguard.service.MailService;
import com.vaultguard.service.TwoFactorService;
import com.vaultguard.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {

    private static final long VERIFY_EMAIL_TOKEN_TTL_SECONDS = 3 * 24 * 3600;

    private final UserService userService;
    private final TwoFactorService twoFactorService;
    private final JwtService jwtService;
    private final MailService mailService;
    private final VaultGuardProperties props;

    public AccountsController(UserService userService, TwoFactorService twoFactorService,
                              JwtService jwtService, MailService mailService,
                              VaultGuardProperties props) {
        this.userService = userService;
        this.twoFactorService = twoFactorService;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.props = props;
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
        String passwordHint = (String) body.get("masterPasswordHint");
        @SuppressWarnings("unchecked")
        Map<String, Object> keys = (Map<String, Object>) body.get("keys");
        String publicKey = keys != null ? (String) keys.get("publicKey") : null;
        String privateKey = keys != null ? (String) keys.get("encryptedPrivateKey") : null;

        User user = userService.register(email, name, masterPasswordHash, kdf, kdfIterations,
            kdfMemory, kdfParallelism, key, privateKey, publicKey);
        if (passwordHint != null && !passwordHint.isBlank()) {
            user.setPasswordHint(passwordHint);
            userService.save(user);
        }
        if (props.isSignupsVerify() && mailService.isEnabled()) {
            sendVerifyEmail(user);
        }
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

    @PostMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfilePost(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        return updateProfile(principal, body);
    }

    @PostMapping("/keys")
    public ResponseEntity<Map<String, Object>> postKeys(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPrivateKey((String) body.get("encryptedPrivateKey"));
        user.setPublicKey((String) body.get("publicKey"));
        userService.save(user);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("PrivateKey", user.getPrivateKey());
        resp.put("PublicKey", user.getPublicKey());
        resp.put("Object", "keys");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/revision-date")
    public ResponseEntity<Long> revisionDate(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(user.getUpdatedAt().toEpochMilli());
    }

    @PostMapping("/verify-password")
    public ResponseEntity<Map<String, Object>> verifyPassword(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, String> body) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String hash = body.get("masterPasswordHash");
        if (hash == null || !userService.verifyPassword(hash, user)) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Invalid password", "Object", "error"));
        }
        return ResponseEntity.ok(Map.of("Object", "masterPasswordPolicy"));
    }

    /** Unauthenticated; mirrors the Rust password_hint contract (no user enumeration). */
    @PostMapping("/password-hint")
    public ResponseEntity<Map<String, Object>> passwordHint(@RequestBody Map<String, String> body) {
        final String noHint = "Sorry, you have no password hint...";
        if (!props.isShowPasswordHint() && !mailService.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "This server is not configured to provide password hints.",
                "Object", "error"));
        }
        String email = body.get("email");
        var userOpt = email != null ? userService.findByEmail(email) : java.util.Optional.<User>empty();
        if (mailService.isEnabled()) {
            userOpt.ifPresent(user -> mailService.send(email, "Your master password hint",
                user.getPasswordHint() != null
                    ? "Your password hint is: " + user.getPasswordHint()
                    : noHint));
            // Act identically whether or not the account exists
            return ResponseEntity.ok().build();
        }
        String hint = userOpt.map(User::getPasswordHint).orElse(null);
        String message = hint != null ? "Your password hint is: " + hint : noHint;
        return ResponseEntity.badRequest().body(Map.of("message", message, "Object", "error"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!mailService.isEnabled()) {
            return ResponseEntity.badRequest().build();
        }
        sendVerifyEmail(user);
        return ResponseEntity.ok().build();
    }

    /** Unauthenticated; completes email verification with {userId, token}. */
    @PostMapping("/verify-email-token")
    public ResponseEntity<Void> verifyEmailToken(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String token = body.get("token");
        if (userId == null || token == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String subject = jwtService.validatePurposeToken(token, "verifyemail");
            if (!subject.equals(userId)) {
                return ResponseEntity.badRequest().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return userService.findById(userId).map(user -> {
            user.setVerifiedAt(Instant.now());
            user.setLoginVerifyCount(0);
            userService.save(user);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.badRequest().build());
    }

    private void sendVerifyEmail(User user) {
        String token = jwtService.issuePurposeToken(user.getUuid(), "verifyemail",
            VERIFY_EMAIL_TOKEN_TTL_SECONDS);
        String link = props.getDomain() + "/#/verify-email?userId=" + user.getUuid()
            + "&token=" + token;
        user.setLastVerifyingAt(Instant.now());
        user.setLoginVerifyCount(user.getLoginVerifyCount() + 1);
        userService.save(user);
        mailService.send(user.getEmail(), "Verify your email",
            "Verify this email address for your account by clicking the link below:\n" + link);
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
        resp.put("TwoFactorEnabled", twoFactorService.hasTwoFactor(user.getUuid()));
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
