package com.vaultguard.service;

import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        VaultGuardProperties props = new VaultGuardProperties();
        userService = new UserService(userRepository, new PasswordHashService(), props);
    }

    @Test
    void registerCreatesUser() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.register("test@example.com", "Test User",
            "master-password-hash", 0, 600000, null, null,
            "protected-sym-key", null, null);

        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getName()).isEqualTo("Test User");
        assertThat(user.getUuid()).isNotBlank();
    }

    @Test
    void registerThrowsIfSignupsDisabled() {
        VaultGuardProperties props = new VaultGuardProperties();
        props.setSignupsAllowed(false);
        UserService svc = new UserService(userRepository, new PasswordHashService(), props);

        assertThatThrownBy(() -> svc.register("test@example.com", "Test",
            "hash", 0, 600000, null, null, "key", null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Registration is disabled");
    }

    @Test
    void registerThrowsIfEmailTaken() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("taken@example.com", "Test",
            "hash", 0, 600000, null, null, "key", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already registered");
    }
}
