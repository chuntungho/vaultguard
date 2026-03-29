package com.vaultguard.api.identity;

import com.vaultguard.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/identity")
public class IdentityController {

    private final AuthService authService;

    public IdentityController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/connect/token")
    public ResponseEntity<Map<String, Object>> token(
        @RequestParam("grant_type") String grantType,
        @RequestParam(value = "username", required = false) String username,
        @RequestParam(value = "password", required = false) String password,
        @RequestParam(value = "refresh_token", required = false) String refreshToken,
        @RequestParam(value = "deviceIdentifier", required = false) String deviceIdentifier,
        @RequestParam(value = "deviceName", required = false) String deviceName,
        @RequestParam(value = "deviceType", required = false, defaultValue = "0") int deviceType
    ) {
        try {
            AuthService.TokenResponse resp;
            if ("password".equals(grantType)) {
                resp = authService.loginWithPassword(username, password,
                    deviceIdentifier, deviceName, deviceType);
            } else if ("refresh_token".equals(grantType)) {
                resp = authService.refreshToken(refreshToken);
            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "unsupported_grant_type"));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", resp.accessToken());
            body.put("expires_in", resp.expiresIn());
            body.put("token_type", resp.tokenType());
            body.put("refresh_token", resp.refreshToken());
            body.put("scope", resp.scope());
            if (resp.key() != null) body.put("Key", resp.key());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid_grant", "error_description", e.getMessage()));
        }
    }
}
