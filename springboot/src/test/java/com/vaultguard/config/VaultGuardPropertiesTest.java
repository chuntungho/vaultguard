package com.vaultguard.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VaultGuardPropertiesTest {

    @Autowired
    private VaultGuardProperties props;

    @Test
    void defaultSignupsAllowed() {
        assertThat(props.isSignupsAllowed()).isTrue();
    }

    @Test
    void defaultJwtExpiry() {
        assertThat(props.getJwt().getAccessTokenExpirySeconds()).isEqualTo(3600);
    }

    @Test
    void defaultRsaKeyPath() {
        assertThat(props.getRsaKeyPath()).isEqualTo("test-rsa-key.pem");
    }
}
