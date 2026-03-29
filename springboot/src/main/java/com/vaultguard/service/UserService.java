package com.vaultguard.service;

import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHashService passwordHashService;
    private final VaultGuardProperties props;

    public UserService(UserRepository userRepository,
                       PasswordHashService passwordHashService,
                       VaultGuardProperties props) {
        this.userRepository = userRepository;
        this.passwordHashService = passwordHashService;
        this.props = props;
    }

    @Transactional
    public User register(String email, String name, String masterPasswordHash,
                         int kdfType, int kdfIterations, Integer kdfMemory, Integer kdfParallelism,
                         String keyHash, String privateKey, String publicKey) {
        if (!props.isSignupsAllowed()) {
            throw new IllegalStateException("Registration is disabled on this server");
        }
        String normalizedEmail = email.toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User();
        user.setUuid(UuidUtil.newUuid());
        user.setEmail(normalizedEmail);
        user.setName(name);
        user.setPasswordHash(passwordHashService.hashForStorage(masterPasswordHash));
        user.setKdfType(kdfType);
        user.setKdfIterations(kdfIterations);
        user.setKdfMemory(kdfMemory);
        user.setKdfParallelism(kdfParallelism);
        user.setKeyHash(keyHash);
        user.setPrivateKey(privateKey);
        user.setPublicKey(publicKey);
        user.setSecurityStamp(UuidUtil.newUuid());
        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase());
    }

    public Optional<User> findById(String uuid) {
        return userRepository.findById(uuid);
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    public boolean verifyPassword(String clientHash, User user) {
        return passwordHashService.verify(clientHash, user.getPasswordHash());
    }
}
