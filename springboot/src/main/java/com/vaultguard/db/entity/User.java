package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "password_hash", nullable = false, columnDefinition = "TEXT")
    private String passwordHash;

    @Column(name = "kdf_type", nullable = false)
    private int kdfType = 0;

    @Column(name = "kdf_iterations", nullable = false)
    private int kdfIterations = 600000;

    @Column(name = "kdf_memory")
    private Integer kdfMemory;

    @Column(name = "kdf_parallelism")
    private Integer kdfParallelism;

    @Column(name = "private_key", columnDefinition = "TEXT")
    private String privateKey;

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "key_hash", columnDefinition = "TEXT")
    private String keyHash;

    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_recover", columnDefinition = "TEXT")
    private String totpRecover;

    @Column(name = "security_stamp", nullable = false)
    private String securityStamp;

    @Column(name = "stamp_exception", columnDefinition = "TEXT")
    private String stampException;

    @Column(name = "password_hint")
    private String passwordHint;

    @Column(name = "equivalent_domains", columnDefinition = "TEXT", nullable = false)
    private String equivalentDomains = "[]";

    @Column(name = "excluded_globals", columnDefinition = "TEXT", nullable = false)
    private String excludedGlobals = "[]";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "email_new")
    private String emailNew;

    @Column(name = "email_new_token")
    private String emailNewToken;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_verifying_at")
    private Instant lastVerifyingAt;

    @Column(name = "login_verify_count")
    private int loginVerifyCount = 0;

    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    // All getters and setters
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public int getKdfType() { return kdfType; }
    public void setKdfType(int kdfType) { this.kdfType = kdfType; }
    public int getKdfIterations() { return kdfIterations; }
    public void setKdfIterations(int kdfIterations) { this.kdfIterations = kdfIterations; }
    public Integer getKdfMemory() { return kdfMemory; }
    public void setKdfMemory(Integer kdfMemory) { this.kdfMemory = kdfMemory; }
    public Integer getKdfParallelism() { return kdfParallelism; }
    public void setKdfParallelism(Integer kdfParallelism) { this.kdfParallelism = kdfParallelism; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public String getTotpRecover() { return totpRecover; }
    public void setTotpRecover(String totpRecover) { this.totpRecover = totpRecover; }
    public String getSecurityStamp() { return securityStamp; }
    public void setSecurityStamp(String securityStamp) { this.securityStamp = securityStamp; }
    public String getStampException() { return stampException; }
    public void setStampException(String stampException) { this.stampException = stampException; }
    public String getPasswordHint() { return passwordHint; }
    public void setPasswordHint(String passwordHint) { this.passwordHint = passwordHint; }
    public String getEquivalentDomains() { return equivalentDomains; }
    public void setEquivalentDomains(String equivalentDomains) { this.equivalentDomains = equivalentDomains; }
    public String getExcludedGlobals() { return excludedGlobals; }
    public void setExcludedGlobals(String excludedGlobals) { this.excludedGlobals = excludedGlobals; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEmailNew() { return emailNew; }
    public void setEmailNew(String emailNew) { this.emailNew = emailNew; }
    public String getEmailNewToken() { return emailNewToken; }
    public void setEmailNewToken(String emailNewToken) { this.emailNewToken = emailNewToken; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getLastVerifyingAt() { return lastVerifyingAt; }
    public void setLastVerifyingAt(Instant lastVerifyingAt) { this.lastVerifyingAt = lastVerifyingAt; }
    public int getLoginVerifyCount() { return loginVerifyCount; }
    public void setLoginVerifyCount(int loginVerifyCount) { this.loginVerifyCount = loginVerifyCount; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
