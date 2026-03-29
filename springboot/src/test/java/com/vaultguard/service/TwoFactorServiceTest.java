package com.vaultguard.service;

import com.vaultguard.db.entity.TwoFactor;
import com.vaultguard.db.repository.TwoFactorRepository;
import com.vaultguard.util.UuidUtil;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TwoFactorServiceTest {

    private TwoFactorRepository twoFactorRepository;
    private TwoFactorService twoFactorService;

    @BeforeEach
    void setUp() {
        twoFactorRepository = Mockito.mock(TwoFactorRepository.class);
        twoFactorService = new TwoFactorService(twoFactorRepository, null);
    }

    @Test
    void generateAndVerifyTotp() throws Exception {
        String secret = twoFactorService.generateTotpSecret();
        assertThat(secret).isNotBlank();
        // Generate a real TOTP code for the secret
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        long timeStep = new SystemTimeProvider().getTime() / 30;
        String code = codeGenerator.generate(secret, timeStep);
        assertThat(twoFactorService.verifyTotp(secret, code)).isTrue();
        assertThat(twoFactorService.verifyTotp(secret, "000000")).isFalse();
    }
}
