package com.vaultguard.api.devices;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Device;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DevicesController {

    private final DeviceRepository deviceRepository;
    private final UserService userService;

    public DevicesController(DeviceRepository deviceRepository, UserService userService) {
        this.deviceRepository = deviceRepository;
        this.userService = userService;
    }

    /** Unauthenticated known-device probe used by clients before login. */
    @GetMapping("/knowndevice")
    public ResponseEntity<Boolean> knownDevice(
        @RequestHeader(value = "X-Device-Identifier", required = false) String deviceIdentifier,
        @RequestHeader(value = "X-Request-Email", required = false) String emailB64) {
        if (deviceIdentifier == null || emailB64 == null) {
            return ResponseEntity.badRequest().build();
        }
        String email;
        try {
            email = new String(Base64.getUrlDecoder().decode(emailB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        boolean known = userService.findByEmail(email)
            .flatMap(user -> deviceRepository.findByUuidAndUserUuid(deviceIdentifier, user.getUuid()))
            .isPresent();
        return ResponseEntity.ok(known);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        List<Map<String, Object>> items = deviceRepository.findByUserUuid(principal.getUserUuid())
            .stream().map(DevicesController::toDeviceResponse).toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Data", items);
        resp.put("ContinuationToken", null);
        resp.put("Object", "list");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/identifier/{deviceId}")
    public ResponseEntity<Map<String, Object>> get(
        @PathVariable String deviceId,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return deviceRepository.findByUuidAndUserUuid(deviceId, principal.getUserUuid())
            .map(d -> ResponseEntity.ok(toDeviceResponse(d)))
            .orElse(ResponseEntity.notFound().build());
    }

    private static Map<String, Object> toDeviceResponse(Device device) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", device.getUuid());
        resp.put("Name", device.getName());
        resp.put("Type", device.getType());
        resp.put("Identifier", device.getUuid());
        resp.put("CreationDate", device.getCreatedAt());
        resp.put("IsTrusted", false);
        resp.put("Object", "device");
        return resp;
    }
}
