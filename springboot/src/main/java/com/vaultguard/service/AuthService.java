package com.vaultguard.service;

import com.vaultguard.auth.JwtService;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Device;
import com.vaultguard.db.entity.TwoFactor;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final UserService userService;
    private final DeviceRepository deviceRepository;
    private final JwtService jwtService;
    private final VaultGuardProperties props;
    private final TwoFactorService twoFactorService;
    private final MailService mailService;

    public AuthService(UserService userService, DeviceRepository deviceRepository,
                       JwtService jwtService, VaultGuardProperties props,
                       TwoFactorService twoFactorService, MailService mailService) {
        this.userService = userService;
        this.deviceRepository = deviceRepository;
        this.jwtService = jwtService;
        this.props = props;
        this.twoFactorService = twoFactorService;
        this.mailService = mailService;
    }

    public record LoginRequest(
        String username,
        String password,
        String deviceIdentifier,
        String deviceName,
        int deviceType,
        Integer twoFactorProvider,
        String twoFactorToken,
        boolean twoFactorRemember,
        String clientId
    ) {}

    @Transactional
    public Map<String, Object> loginWithPassword(LoginRequest request) {
        User user = userService.findByEmail(request.username())
            .orElseThrow(() -> new IllegalArgumentException("Username or password is incorrect. Try again"));
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("This user has been disabled");
        }
        if (!userService.verifyPassword(request.password(), user)) {
            throw new IllegalArgumentException("Username or password is incorrect. Try again");
        }
        if (props.isSignupsVerify() && mailService.isEnabled() && user.getVerifiedAt() == null) {
            throw new IllegalArgumentException("Please verify your email before trying again.");
        }

        Device device = findOrCreateDevice(user, request);

        String rememberToken = twoFactorAuth(user, device, request);

        device.setLastActive(Instant.now());
        device.setRefreshToken(UuidUtil.newUuid() + UuidUtil.newUuid());
        deviceRepository.save(device);

        String accessToken = jwtService.issueAccessToken(user, device, request.clientId());
        Map<String, Object> result = tokenResponse(accessToken, device.getRefreshToken(), user);
        if (rememberToken != null) {
            result.put("TwoFactorToken", rememberToken);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> refreshToken(String refreshToken) {
        Device device = deviceRepository.findByRefreshToken(refreshToken)
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        User user = userService.findById(device.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("This user has been disabled");
        }

        device.setRefreshToken(UuidUtil.newUuid() + UuidUtil.newUuid());
        device.setLastActive(Instant.now());
        deviceRepository.save(device);

        String accessToken = jwtService.issueAccessToken(user, device, null);
        return tokenResponse(accessToken, device.getRefreshToken(), user);
    }

    /**
     * Devices are keyed by the client-generated device identifier (as in Rust,
     * where devices.uuid IS the identifier sent with each login).
     */
    private Device findOrCreateDevice(User user, LoginRequest request) {
        String identifier = request.deviceIdentifier() != null
            ? request.deviceIdentifier() : UuidUtil.newUuid();
        return deviceRepository.findByUuidAndUserUuid(identifier, user.getUuid())
            .orElseGet(() -> {
                Device d = new Device();
                // A colliding identifier registered by another user gets a fresh uuid
                d.setUuid(deviceRepository.existsByUuid(identifier) ? UuidUtil.newUuid() : identifier);
                d.setUserUuid(user.getUuid());
                d.setName(request.deviceName() != null ? request.deviceName() : identifier);
                d.setType(request.deviceType());
                return d;
            });
    }

    /**
     * Mirrors the Rust twofactor_auth flow: no factors → pass; otherwise the token is
     * validated against the selected provider; Remember (5) and RecoveryCode (8) are
     * always acceptable. Returns the new remember token when requested.
     */
    private String twoFactorAuth(User user, Device device, LoginRequest request) {
        List<TwoFactor> factors = twoFactorService.getEnabledFactors(user.getUuid());
        if (factors.isEmpty()) {
            return null;
        }
        List<Integer> providerIds = factors.stream().map(TwoFactor::getType).toList();

        int selected = request.twoFactorProvider() != null
            ? request.twoFactorProvider() : providerIds.get(0);
        boolean special = selected == TwoFactorService.TYPE_REMEMBER
            || selected == TwoFactorService.TYPE_RECOVERY_CODE;
        if (!special && !providerIds.contains(selected)) {
            throw twoFactorRequired(user, providerIds);
        }
        if (request.twoFactorToken() == null) {
            throw twoFactorRequired(user, providerIds);
        }

        if (selected == TwoFactorService.TYPE_REMEMBER) {
            String stored = device.getTwofactorRemember();
            if (stored == null || !constantTimeEquals(stored, request.twoFactorToken())) {
                device.setTwofactorRemember(null);
                deviceRepository.save(device);
                throw twoFactorRequired(user, providerIds);
            }
        } else if (selected == TwoFactorService.TYPE_RECOVERY_CODE) {
            if (!twoFactorService.useRecoveryCode(user, request.twoFactorToken())) {
                throw new IllegalArgumentException("Recovery code is incorrect. Try again.");
            }
        } else if (!twoFactorService.validateTwoFactor(user.getUuid(), selected, request.twoFactorToken())) {
            throw twoFactorRequired(user, providerIds);
        }

        if (request.twoFactorRemember()) {
            String rememberToken = UuidUtil.newUuid() + UuidUtil.newUuid();
            device.setTwofactorRemember(rememberToken);
            return rememberToken;
        }
        return null;
    }

    private TwoFactorRequiredException twoFactorRequired(User user, List<Integer> providerIds) {
        // Email is sent automatically when it is the only available provider (as in Rust)
        if (providerIds.size() == 1 && providerIds.get(0) == TwoFactorService.TYPE_EMAIL) {
            try {
                twoFactorService.sendEmailLoginCode(user.getUuid());
            } catch (Exception ignored) {
                // mail failures must not turn into login errors here
            }
        }
        return new TwoFactorRequiredException(user.getUuid(), providerIds,
            twoFactorService.providerMetadata(user.getUuid()));
    }

    private Map<String, Object> tokenResponse(String accessToken, String refreshToken, User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", accessToken);
        result.put("expires_in", props.getJwt().getAccessTokenExpirySeconds());
        result.put("token_type", "Bearer");
        result.put("refresh_token", refreshToken);
        result.put("PrivateKey", user.getPrivateKey());
        result.put("Kdf", user.getKdfType());
        result.put("KdfIterations", user.getKdfIterations());
        result.put("KdfMemory", user.getKdfMemory());
        result.put("KdfParallelism", user.getKdfParallelism());
        result.put("ResetMasterPassword", false);
        result.put("ForcePasswordReset", false);
        result.put("MasterPasswordPolicy", Map.of("Object", "masterPasswordPolicy"));
        result.put("scope", "api offline_access");
        result.put("UserDecryptionOptions", Map.of(
            "HasMasterPassword", user.getPasswordHash() != null && !user.getPasswordHash().isEmpty(),
            "Object", "userDecryptionOptions"));
        if (user.getKeyHash() != null) {
            result.put("Key", user.getKeyHash());
        }
        return result;
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
