package com.vaultguard.service;

import com.vaultguard.auth.JwtService;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Device;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserService userService;
    private final DeviceRepository deviceRepository;
    private final JwtService jwtService;
    private final VaultGuardProperties props;
    private final TwoFactorService twoFactorService;

    public AuthService(UserService userService, DeviceRepository deviceRepository,
                       JwtService jwtService, VaultGuardProperties props,
                       TwoFactorService twoFactorService) {
        this.userService = userService;
        this.deviceRepository = deviceRepository;
        this.jwtService = jwtService;
        this.props = props;
        this.twoFactorService = twoFactorService;
    }

    public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        String scope,
        String key,
        Boolean twoFactorRequired
    ) {}

    @Transactional
    public TokenResponse loginWithPassword(String username, String password,
                                           String deviceIdentifier, String deviceName, int deviceType,
                                           Integer twoFactorType, String twoFactorToken) {
        User user = userService.findByEmail(username)
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }
        if (!userService.verifyPassword(password, user)) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (twoFactorService.hasTwoFactor(user.getUuid())) {
            if (twoFactorType == null || twoFactorToken == null ||
                !twoFactorService.validateTwoFactor(user.getUuid(), twoFactorType, twoFactorToken)) {
                throw new TwoFactorRequiredException(user.getUuid());
            }
        }

        Device device = deviceRepository.findByUserUuidAndName(user.getUuid(), deviceIdentifier)
            .orElseGet(() -> {
                Device d = new Device();
                d.setUuid(UuidUtil.newUuid());
                d.setUserUuid(user.getUuid());
                d.setName(deviceIdentifier);
                d.setType(deviceType);
                return d;
            });
        device.setLastActive(Instant.now());
        device.setRefreshToken(UuidUtil.newUuid());
        deviceRepository.save(device);

        String accessToken = jwtService.issueAccessToken(user.getUuid(), device.getUuid());
        return new TokenResponse(
            accessToken,
            device.getRefreshToken(),
            props.getJwt().getAccessTokenExpirySeconds(),
            "Bearer",
            "api offline_access",
            user.getKeyHash(),
            null
        );
    }

    @Transactional
    public TokenResponse refreshToken(String refreshToken) {
        Device device = deviceRepository.findAll().stream()
            .filter(d -> refreshToken.equals(d.getRefreshToken()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        User user = userService.findById(device.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        device.setRefreshToken(UuidUtil.newUuid());
        device.setLastActive(Instant.now());
        deviceRepository.save(device);

        String accessToken = jwtService.issueAccessToken(user.getUuid(), device.getUuid());
        return new TokenResponse(
            accessToken,
            device.getRefreshToken(),
            props.getJwt().getAccessTokenExpirySeconds(),
            "Bearer",
            "api offline_access",
            user.getKeyHash(),
            null
        );
    }
}
