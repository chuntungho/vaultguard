package com.vaultguard.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import com.vaultguard.config.VaultGuardProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final RSAKey rsaKey;
    private final VaultGuardProperties props;

    public JwtService(RSAKey rsaKey, VaultGuardProperties props) {
        this.rsaKey = rsaKey;
        this.props = props;
    }

    public String issueAccessToken(String userUuid, String deviceUuid) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(props.getJwt().getAccessTokenExpirySeconds());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userUuid)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .jwtID(UUID.randomUUID().toString())
                .claim("device", deviceUuid)
                .claim("premium", false)
                .build();

            SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims
            );
            jwt.sign(new RSASSASigner(rsaKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to issue access token", e);
        }
    }

    public ParsedToken validateAccessToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSASSAVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            if (!jwt.verify(verifier)) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                throw new IllegalArgumentException("JWT expired");
            }
            return new ParsedToken(
                claims.getSubject(),
                (String) claims.getClaim("device")
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT: " + e.getMessage(), e);
        }
    }

    public record ParsedToken(String userUuid, String deviceUuid) {}
}
