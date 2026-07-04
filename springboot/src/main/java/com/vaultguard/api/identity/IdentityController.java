package com.vaultguard.api.identity;

import com.vaultguard.api.accounts.AccountsController;
import com.vaultguard.service.AuthService;
import com.vaultguard.service.TwoFactorRequiredException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/identity")
public class IdentityController {

    private final AuthService authService;
    private final AccountsController accountsController;

    public IdentityController(AuthService authService, AccountsController accountsController) {
        this.authService = authService;
        this.accountsController = accountsController;
    }

    @PostMapping("/connect/token")
    public ResponseEntity<Map<String, Object>> token(
        @RequestParam("grant_type") String grantType,
        @RequestParam(value = "username", required = false) String username,
        @RequestParam(value = "password", required = false) String password,
        @RequestParam(value = "refresh_token", required = false) String refreshToken,
        @RequestParam(value = "client_id", required = false) String clientId,
        @RequestParam(value = "deviceIdentifier", required = false) String deviceIdentifier,
        @RequestParam(value = "deviceName", required = false) String deviceName,
        @RequestParam(value = "deviceType", required = false, defaultValue = "0") int deviceType,
        @RequestParam(value = "twoFactorToken", required = false) String twoFactorToken,
        @RequestParam(value = "twoFactorProvider", required = false) Integer twoFactorProvider,
        @RequestParam(value = "twoFactorRemember", required = false, defaultValue = "0") int twoFactorRemember
    ) {
        try {
            Map<String, Object> body;
            if ("password".equals(grantType)) {
                body = authService.loginWithPassword(new AuthService.LoginRequest(
                    username, password, deviceIdentifier, deviceName, deviceType,
                    twoFactorProvider, twoFactorToken, twoFactorRemember == 1, clientId));
            } else if ("refresh_token".equals(grantType)) {
                body = authService.refreshToken(refreshToken);
            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "unsupported_grant_type"));
            }
            return ResponseEntity.ok(body);
        } catch (TwoFactorRequiredException e) {
            // Same error contract as the Rust json_err_twofactor
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "invalid_grant");
            body.put("error_description", "Two factor required.");
            body.put("TwoFactorProviders", e.getProviders().stream().map(String::valueOf).toList());
            body.put("TwoFactorProviders2", e.getProviderMetadata());
            body.put("MasterPasswordPolicy", Map.of("Object", "masterPasswordPolicy"));
            return ResponseEntity.badRequest().body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid_grant", "error_description", e.getMessage()));
        }
    }

    // Newer clients register/prelogin against the identity service (as in Rust identity.rs)
    @PostMapping("/accounts/register")
    public ResponseEntity<Void> register(@RequestBody Map<String, Object> body) {
        return accountsController.register(body);
    }

    @PostMapping("/accounts/prelogin")
    public ResponseEntity<Map<String, Object>> prelogin(@RequestBody Map<String, String> body) {
        return accountsController.prelogin(body);
    }
}
