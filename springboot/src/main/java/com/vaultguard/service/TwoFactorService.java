package com.vaultguard.service;

import tools.jackson.databind.ObjectMapper;
import com.vaultguard.db.entity.TwoFactor;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.TwoFactorRepository;
import com.vaultguard.db.repository.UserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Two-factor providers. Types match the Rust TwoFactorType enum:
 * 0 Authenticator (TOTP), 1 Email, 5 Remember, 8 RecoveryCode,
 * 1002 EmailVerificationChallenge (pending email-2FA setup).
 */
@Service
public class TwoFactorService {

    public static final int TYPE_TOTP = 0;
    public static final int TYPE_EMAIL = 1;
    public static final int TYPE_REMEMBER = 5;
    public static final int TYPE_RECOVERY_CODE = 8;
    public static final int TYPE_EMAIL_CHALLENGE = 1002;

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long EMAIL_TOKEN_TTL_SECONDS = 600;

    private final TwoFactorRepository twoFactorRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(TwoFactorRepository twoFactorRepository,
                            UserRepository userRepository,
                            MailService mailService) {
        this.twoFactorRepository = twoFactorRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
    }

    public String generateTotpSecret() {
        return secretGenerator.generate();
    }

    public boolean verifyTotp(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    /** Enabled login providers (TOTP/email), excluding setup challenges. */
    public List<TwoFactor> getEnabledFactors(String userUuid) {
        return twoFactorRepository.findByUserUuidAndEnabled(userUuid, true).stream()
            .filter(tf -> tf.getType() == TYPE_TOTP || tf.getType() == TYPE_EMAIL)
            .toList();
    }

    public boolean hasTwoFactor(String userUuid) {
        return !getEnabledFactors(userUuid).isEmpty();
    }

    /** All provider rows for the management screen (excludes setup challenges). */
    public List<TwoFactor> getProviders(String userUuid) {
        return twoFactorRepository.findByUserUuid(userUuid).stream()
            .filter(tf -> tf.getType() < 1000)
            .toList();
    }

    /** Per-provider metadata for the login TwoFactorProviders2 map. */
    public Map<String, Object> providerMetadata(String userUuid) {
        Map<String, Object> meta = new LinkedHashMap<>();
        for (TwoFactor tf : getEnabledFactors(userUuid)) {
            if (tf.getType() == TYPE_EMAIL) {
                meta.put(String.valueOf(TYPE_EMAIL), Map.of("Email", obscureEmail(emailOf(tf))));
            } else {
                meta.put(String.valueOf(tf.getType()), null);
            }
        }
        return meta;
    }

    @Transactional
    public TwoFactor enableTotp(String userUuid, String secret) {
        TwoFactor tf = twoFactorRepository.findByUserUuidAndType(userUuid, TYPE_TOTP)
            .orElseGet(() -> {
                TwoFactor t = new TwoFactor();
                t.setUserUuid(userUuid);
                t.setType(TYPE_TOTP);
                return t;
            });
        tf.setData(writeJson(Map.of("Secret", secret)));
        tf.setEnabled(true);
        TwoFactor saved = twoFactorRepository.save(tf);
        ensureRecoveryCode(userUuid);
        return saved;
    }

    @Transactional
    public void disable(String userUuid, int type) {
        twoFactorRepository.findByUserUuidAndType(userUuid, type)
            .ifPresent(twoFactorRepository::delete);
    }

    @Transactional
    public void deleteAll(String userUuid) {
        twoFactorRepository.deleteAll(twoFactorRepository.findByUserUuid(userUuid));
    }

    public Optional<String> totpSecret(String userUuid) {
        return twoFactorRepository.findByUserUuidAndType(userUuid, TYPE_TOTP)
            .map(tf -> (String) readJson(tf.getData()).get("Secret"));
    }

    public boolean validateTwoFactor(String userUuid, int twoFactorType, String twoFactorToken) {
        Optional<TwoFactor> tfOpt = twoFactorRepository.findByUserUuidAndType(userUuid, twoFactorType);
        if (tfOpt.isEmpty() || !tfOpt.get().isEnabled()) return false;
        TwoFactor tf = tfOpt.get();

        if (twoFactorType == TYPE_TOTP) {
            String secret = (String) readJson(tf.getData()).get("Secret");
            return secret != null && verifyTotp(secret, twoFactorToken);
        }
        if (twoFactorType == TYPE_EMAIL) {
            return consumeEmailToken(tf, twoFactorToken);
        }
        return false;
    }

    /** Starts email-2FA setup: stores a pending challenge and mails the token. */
    @Transactional
    public void startEmailSetup(String userUuid, String email) {
        twoFactorRepository.findByUserUuidAndType(userUuid, TYPE_EMAIL_CHALLENGE)
            .ifPresent(twoFactorRepository::delete);
        TwoFactor challenge = new TwoFactor();
        challenge.setUserUuid(userUuid);
        challenge.setType(TYPE_EMAIL_CHALLENGE);
        challenge.setEnabled(false);
        String token = generateEmailToken();
        challenge.setData(writeJson(Map.of(
            "Email", email,
            "LastToken", token,
            "Expires", Instant.now().plusSeconds(EMAIL_TOKEN_TTL_SECONDS).toString())));
        twoFactorRepository.save(challenge);
        sendTokenMail(email, token);
    }

    /** Completes email-2FA setup: verifies the challenge token and activates provider 1. */
    @Transactional
    public boolean activateEmail(String userUuid, String email, String token) {
        Optional<TwoFactor> challengeOpt =
            twoFactorRepository.findByUserUuidAndType(userUuid, TYPE_EMAIL_CHALLENGE);
        if (challengeOpt.isEmpty()) return false;
        TwoFactor challenge = challengeOpt.get();
        Map<String, Object> data = readJson(challenge.getData());
        if (!token.equals(data.get("LastToken")) || !email.equals(data.get("Email"))) {
            return false;
        }
        twoFactorRepository.delete(challenge);

        TwoFactor tf = twoFactorRepository.findByUserUuidAndType(userUuid, TYPE_EMAIL)
            .orElseGet(() -> {
                TwoFactor t = new TwoFactor();
                t.setUserUuid(userUuid);
                t.setType(TYPE_EMAIL);
                return t;
            });
        tf.setData(writeJson(Map.of("Email", email)));
        tf.setEnabled(true);
        twoFactorRepository.save(tf);
        ensureRecoveryCode(userUuid);
        return true;
    }

    public Optional<String> emailConfig(String userUuid) {
        return twoFactorRepository.findByUserUuidAndType(userUuid, TYPE_EMAIL)
            .filter(TwoFactor::isEnabled)
            .map(this::emailOf);
    }

    /** Generates and mails a fresh login token for the enabled email provider. */
    @Transactional
    public void sendEmailLoginCode(String userUuid) {
        TwoFactor tf = twoFactorRepository.findByUserUuidAndType(userUuid, TYPE_EMAIL)
            .filter(TwoFactor::isEnabled)
            .orElseThrow(() -> new IllegalArgumentException("No email two-factor configured"));
        Map<String, Object> data = readJson(tf.getData());
        String token = generateEmailToken();
        data.put("LastToken", token);
        data.put("Expires", Instant.now().plusSeconds(EMAIL_TOKEN_TTL_SECONDS).toString());
        tf.setData(writeJson(data));
        twoFactorRepository.save(tf);
        sendTokenMail(emailOf(tf), token);
    }

    /** Generates the one-time recovery code if the user does not have one yet. */
    @Transactional
    public void ensureRecoveryCode(String userUuid) {
        userRepository.findById(userUuid).ifPresent(user -> {
            if (user.getTotpRecover() == null) {
                // 20 random bytes → 32 base32 chars, same as the Rust implementation
                user.setTotpRecover(randomBase32(32));
                userRepository.save(user);
            }
        });
    }

    /** Recovery-code login: wipes all providers and the code itself, as in Rust. */
    @Transactional
    public boolean useRecoveryCode(User user, String code) {
        String recover = user.getTotpRecover();
        if (recover == null || code == null || !recover.equalsIgnoreCase(code.trim())) {
            return false;
        }
        deleteAll(user.getUuid());
        user.setTotpRecover(null);
        userRepository.save(user);
        return true;
    }

    private boolean consumeEmailToken(TwoFactor tf, String token) {
        Map<String, Object> data = readJson(tf.getData());
        String stored = (String) data.get("LastToken");
        String expires = (String) data.get("Expires");
        if (stored == null || token == null || expires == null) return false;
        if (Instant.parse(expires).isBefore(Instant.now())) return false;
        if (!stored.equals(token.trim())) return false;
        // Single use
        data.remove("LastToken");
        data.remove("Expires");
        tf.setData(writeJson(data));
        tf.setLastUsed(Instant.now());
        twoFactorRepository.save(tf);
        return true;
    }

    private void sendTokenMail(String email, String token) {
        mailService.send(email, "Your VaultGuard verification code",
            "Your verification code is: " + token + "\nExpires in 10 minutes.");
    }

    private String emailOf(TwoFactor tf) {
        Object email = readJson(tf.getData()).get("Email");
        return email != null ? email.toString() : "";
    }

    private String generateEmailToken() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String randomBase32(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE32_ALPHABET.charAt(random.nextInt(BASE32_ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String obscureEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String name = email.substring(0, at);
        String obscured = switch (name.length()) {
            case 1 -> "*";
            case 2, 3 -> name.charAt(0) + "*".repeat(name.length() - 1);
            default -> name.substring(0, 2) + "*".repeat(name.length() - 2);
        };
        return obscured + email.substring(at);
    }

    private Map<String, Object> readJson(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, LinkedHashMap.class);
            return map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize two-factor data", e);
        }
    }
}
