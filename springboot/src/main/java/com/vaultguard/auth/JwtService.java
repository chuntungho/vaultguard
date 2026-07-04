package com.vaultguard.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Device;
import com.vaultguard.db.entity.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final RSAKey rsaKey;
    private final VaultGuardProperties props;

    public JwtService(RSAKey rsaKey, VaultGuardProperties props) {
        this.rsaKey = rsaKey;
        this.props = props;
    }

    /**
     * Full login token carrying the same claim set as the Rust implementation
     * (LoginJwtClaims): identity, security stamp for session invalidation,
     * device binding, scope and amr.
     */
    public String issueAccessToken(User user, Device device, String clientId) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(props.getJwt().getAccessTokenExpirySeconds());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(props.getDomain() + "|login")
                .subject(user.getUuid())
                .notBeforeTime(Date.from(now))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .jwtID(UUID.randomUUID().toString())
                .claim("premium", true)
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("email_verified", user.getVerifiedAt() != null)
                .claim("sstamp", user.getSecurityStamp())
                .claim("device", device.getUuid())
                .claim("devicetype", String.valueOf(device.getType()))
                .claim("client_id", clientId != null ? clientId : "undefined")
                .claim("scope", List.of("api", "offline_access"))
                .claim("amr", List.of("Application"))
                .build();

            return sign(claims);
        } catch (Exception e) {
            throw new RuntimeException("Failed to issue access token", e);
        }
    }

    /** Minimal token (kept for tooling/tests); carries no security stamp. */
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

            return sign(claims);
        } catch (Exception e) {
            throw new RuntimeException("Failed to issue access token", e);
        }
    }

    /** Single-purpose token (e.g. email verification), scoped by an explicit purpose claim. */
    public String issuePurposeToken(String subject, String purpose, long ttlSeconds) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(props.getDomain() + "|" + purpose)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .jwtID(UUID.randomUUID().toString())
                .claim("purpose", purpose)
                .build();
            return sign(claims);
        } catch (Exception e) {
            throw new RuntimeException("Failed to issue token", e);
        }
    }

    /** Validates a purpose token and returns its subject, or throws IllegalArgumentException. */
    public String validatePurposeToken(String token, String purpose) {
        JWTClaimsSet claims = verify(token);
        if (!purpose.equals(claims.getClaim("purpose"))) {
            throw new IllegalArgumentException("Invalid token purpose");
        }
        return claims.getSubject();
    }

    public ParsedToken validateAccessToken(String token) {
        JWTClaimsSet claims = verify(token);
        if (claims.getClaim("purpose") != null) {
            throw new IllegalArgumentException("Not an access token");
        }
        return new ParsedToken(
            claims.getSubject(),
            (String) claims.getClaim("device"),
            (String) claims.getClaim("sstamp")
        );
    }

    private String sign(JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
            claims
        );
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    private JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSASSAVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            if (!jwt.verify(verifier)) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new IllegalArgumentException("JWT expired");
            }
            return claims;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT: " + e.getMessage(), e);
        }
    }

    public record ParsedToken(String userUuid, String deviceUuid, String securityStamp) {}
}
