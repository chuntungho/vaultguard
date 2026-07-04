package com.vaultguard.api.meta;

import com.vaultguard.config.VaultGuardProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unauthenticated client-discovery and liveness endpoints, mirroring the Rust
 * core meta routes (config, alive, now, version).
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    /** Bitwarden server version the API surface is compatible with (see Rust config()). */
    public static final String COMPAT_VERSION = "2025.12.0";

    private final VaultGuardProperties props;

    public MetaController(VaultGuardProperties props) {
        this.props = props;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        String domain = props.getDomain();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("version", COMPAT_VERSION);
        resp.put("gitHash", null);
        resp.put("server", Map.of(
            "name", "VaultGuard",
            "url", "https://github.com/chuntungho/vaultguard"));
        resp.put("settings", Map.of("disableUserRegistration", !props.isSignupsAllowed()));
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("vault", domain);
        environment.put("api", domain + "/api");
        environment.put("identity", domain + "/identity");
        environment.put("notifications", domain + "/notifications");
        environment.put("sso", "");
        environment.put("cloudRegion", null);
        resp.put("environment", environment);
        Map<String, Object> push = new LinkedHashMap<>();
        push.put("pushTechnology", 0);
        push.put("vapidPublicKey", null);
        resp.put("push", push);
        resp.put("featureStates", Map.of());
        resp.put("object", "config");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/alive")
    public ResponseEntity<String> alive() {
        return ResponseEntity.ok("\"" + Instant.now() + "\"");
    }

    @GetMapping("/now")
    public ResponseEntity<String> now() {
        return alive();
    }

    @GetMapping("/version")
    public ResponseEntity<String> version() {
        return ResponseEntity.ok("\"" + COMPAT_VERSION + "\"");
    }
}
