package com.vaultguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.db.entity.TwoFactor;
import com.vaultguard.db.repository.TwoFactorRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TwoFactorService {

    public static final int TYPE_TOTP = 0;
    public static final int TYPE_EMAIL = 1;

    private final TwoFactorRepository twoFactorRepository;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());

    public TwoFactorService(TwoFactorRepository twoFactorRepository, JavaMailSender mailSender) {
        this.twoFactorRepository = twoFactorRepository;
        this.mailSender = mailSender;
    }

    public String generateTotpSecret() {
        return secretGenerator.generate();
    }

    public boolean verifyTotp(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    public List<TwoFactor> getEnabledFactors(String userUuid) {
        return twoFactorRepository.findByUserUuidAndEnabled(userUuid, true);
    }

    public boolean hasTwoFactor(String userUuid) {
        return !getEnabledFactors(userUuid).isEmpty();
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
        try {
            tf.setData(objectMapper.writeValueAsString(Map.of("Secret", secret)));
        } catch (Exception e) {
            tf.setData("{\"Secret\":\"" + secret + "\"}");
        }
        tf.setEnabled(true);
        return twoFactorRepository.save(tf);
    }

    public boolean validateTwoFactor(String userUuid, int twoFactorType, String twoFactorToken) {
        Optional<TwoFactor> tfOpt = twoFactorRepository.findByUserUuidAndType(userUuid, twoFactorType);
        if (tfOpt.isEmpty() || !tfOpt.get().isEnabled()) return false;
        TwoFactor tf = tfOpt.get();

        if (twoFactorType == TYPE_TOTP) {
            try {
                Map<?, ?> data = objectMapper.readValue(tf.getData(), Map.class);
                String secret = (String) data.get("Secret");
                return verifyTotp(secret, twoFactorToken);
            } catch (Exception e) {
                return false;
            }
        }
        if (twoFactorType == TYPE_EMAIL) {
            try {
                Map<?, ?> data = objectMapper.readValue(tf.getData(), Map.class);
                String storedCode = (String) data.get("Code");
                String expiresStr = (String) data.get("Expires");
                if (storedCode == null || expiresStr == null) return false;
                if (Instant.parse(expiresStr).isBefore(Instant.now())) return false;
                return storedCode.equals(twoFactorToken);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Transactional
    public void sendEmailCode(String userUuid, String email) {
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        Instant expires = Instant.now().plusSeconds(600);
        TwoFactor tf = twoFactorRepository.findByUserUuidAndType(userUuid, TYPE_EMAIL)
            .orElseGet(() -> {
                TwoFactor t = new TwoFactor();
                t.setUserUuid(userUuid);
                t.setType(TYPE_EMAIL);
                return t;
            });
        try {
            tf.setData(objectMapper.writeValueAsString(Map.of("Code", code, "Expires", expires.toString())));
        } catch (Exception e) {
            tf.setData("{\"Code\":\"" + code + "\",\"Expires\":\"" + expires + "\"}");
        }
        tf.setEnabled(true);
        twoFactorRepository.save(tf);

        if (mailSender != null) {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(email);
            msg.setSubject("Your VaultGuard verification code");
            msg.setText("Your verification code is: " + code + "\nExpires in 10 minutes.");
            try { mailSender.send(msg); } catch (Exception ignored) {}
        }
    }
}
