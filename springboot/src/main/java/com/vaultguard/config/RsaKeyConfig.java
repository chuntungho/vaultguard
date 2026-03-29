package com.vaultguard.config;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class RsaKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyConfig.class);

    @Bean
    public RSAKey rsaKey(VaultGuardProperties props) throws Exception {
        Path keyPath = Path.of(props.getRsaKeyPath());
        if (Files.exists(keyPath)) {
            return loadFromDisk(keyPath, props);
        }
        log.warn("RSA key not found at {}; generating ephemeral key pair (NOT for production)", keyPath);
        return new RSAKeyGenerator(2048).keyID("vaultguard").generate();
    }

    private RSAKey loadFromDisk(Path privatePath, VaultGuardProperties props) throws Exception {
        Path publicPath = Path.of(props.getRsaKeyPath().replace(".pem", ".pub.pem"));
        String privPem = Files.readString(privatePath)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        String pubPem = Files.readString(publicPath)
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");

        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(
            new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privPem)));
        RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(
            new X509EncodedKeySpec(Base64.getDecoder().decode(pubPem)));

        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("vaultguard").build();
    }
}
