package com.vaultguard.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

@Component
public class PasswordHashService {

    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;

    public String hashForStorage(String clientHash) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            byte[] hash = pbkdf2(clientHash.toCharArray(), salt);
            return Base64.getEncoder().encodeToString(salt) + ":" +
                   Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    public boolean verify(String clientHash, String storedHash) {
        try {
            String[] parts = storedHash.split(":", 2);
            if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
            byte[] actualHash = pbkdf2(clientHash.toCharArray(), salt);
            return constantTimeEquals(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] pbkdf2(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
        return skf.generateSecret(spec).getEncoded();
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
