package com.vaultguard.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashServiceTest {

    private final PasswordHashService service = new PasswordHashService();

    @Test
    void hashAndVerify() {
        String clientHash = "client-stretched-hash-base64==";
        String serverHash = service.hashForStorage(clientHash);
        assertThat(serverHash).isNotEqualTo(clientHash);
        assertThat(service.verify(clientHash, serverHash)).isTrue();
    }

    @Test
    void differentInputsDifferentHashes() {
        String hash1 = service.hashForStorage("input1");
        String hash2 = service.hashForStorage("input2");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void wrongInputDoesNotVerify() {
        String serverHash = service.hashForStorage("correct-input");
        assertThat(service.verify("wrong-input", serverHash)).isFalse();
    }
}
