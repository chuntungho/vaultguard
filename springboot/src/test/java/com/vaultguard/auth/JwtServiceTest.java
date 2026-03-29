package com.vaultguard.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.vaultguard.config.VaultGuardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        VaultGuardProperties props = new VaultGuardProperties();
        jwtService = new JwtService(rsaKey, props);
    }

    @Test
    void issueAndValidateAccessToken() {
        String token = jwtService.issueAccessToken("user-uuid-123", "device-uuid-456");
        assertThat(token).isNotBlank();
        JwtService.ParsedToken parsed = jwtService.validateAccessToken(token);
        assertThat(parsed.userUuid()).isEqualTo("user-uuid-123");
        assertThat(parsed.deviceUuid()).isEqualTo("device-uuid-456");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issueAccessToken("user-uuid-123", "device-uuid-456");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThatThrownBy(() -> jwtService.validateAccessToken(tampered))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        VaultGuardProperties props = new VaultGuardProperties();
        props.getJwt().setAccessTokenExpirySeconds(-1); // already expired
        JwtService expiredService = new JwtService(rsaKey, props);
        String token = expiredService.issueAccessToken("user-uuid-123", "device-uuid-456");
        assertThatThrownBy(() -> jwtService.validateAccessToken(token))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
