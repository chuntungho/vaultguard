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

    @Test
    void adminCorsOriginsIsNeverNull() {
        // The list field is initialized to new ArrayList<>() — verify it is never null
        // (application-test.properties sets a value, so isEmpty() is not asserted here)
        assertThat(props.getAdminCorsOrigins()).isNotNull();
    }

    @Test
    void adminCorsOriginsBindsFromTestProperties() {
        // application-test.properties sets vaultguard.admin-cors-origins=http://localhost:5173
        assertThat(props.getAdminCorsOrigins()).contains("http://localhost:5173");
    }
}
