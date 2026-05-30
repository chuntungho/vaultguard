package com.vaultguard.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vaultguard")
public class VaultGuardProperties {

    private String domain = "http://localhost:8080";
    private boolean signupsAllowed = true;
    private boolean invitationsAllowed = true;
    private int passwordIterations = 600000;
    private String rsaKeyPath = "rsa_key.pem";
    private String attachmentsPath = "data/attachments";
    private String adminToken = "";
    private String webVaultPath = "";
    private List<String> adminCorsOrigins = new ArrayList<>();
    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();
    private Mail mail = new Mail();

    public static class Jwt {
        private long accessTokenExpirySeconds = 3600;
        private int refreshTokenExpiryDays = 30;

        public long getAccessTokenExpirySeconds() { return accessTokenExpirySeconds; }
        public void setAccessTokenExpirySeconds(long v) { this.accessTokenExpirySeconds = v; }
        public int getRefreshTokenExpiryDays() { return refreshTokenExpiryDays; }
        public void setRefreshTokenExpiryDays(int v) { this.refreshTokenExpiryDays = v; }
    }

    public static class RateLimit {
        private int maxRequests = 20;
        private int windowSeconds = 60;

        public int getMaxRequests() { return maxRequests; }
        public void setMaxRequests(int v) { this.maxRequests = v; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int v) { this.windowSeconds = v; }
    }

    public static class Mail {
        private String from = "no-reply@vaultguard.local";
        private String fromName = "VaultGuard";

        public String getFrom() { return from; }
        public void setFrom(String v) { this.from = v; }
        public String getFromName() { return fromName; }
        public void setFromName(String v) { this.fromName = v; }
    }

    public String getDomain() { return domain; }
    public void setDomain(String v) { this.domain = v; }
    public boolean isSignupsAllowed() { return signupsAllowed; }
    public void setSignupsAllowed(boolean v) { this.signupsAllowed = v; }
    public boolean isInvitationsAllowed() { return invitationsAllowed; }
    public void setInvitationsAllowed(boolean v) { this.invitationsAllowed = v; }
    public int getPasswordIterations() { return passwordIterations; }
    public void setPasswordIterations(int v) { this.passwordIterations = v; }
    public String getRsaKeyPath() { return rsaKeyPath; }
    public void setRsaKeyPath(String v) { this.rsaKeyPath = v; }
    public String getAttachmentsPath() { return attachmentsPath; }
    public void setAttachmentsPath(String v) { this.attachmentsPath = v; }
    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String v) { this.adminToken = v; }
    public String getWebVaultPath() { return webVaultPath; }
    public void setWebVaultPath(String v) { this.webVaultPath = v; }
    public List<String> getAdminCorsOrigins() { return adminCorsOrigins; }
    public void setAdminCorsOrigins(List<String> v) { this.adminCorsOrigins = v; }
    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt v) { this.jwt = v; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit v) { this.rateLimit = v; }
    public Mail getMail() { return mail; }
    public void setMail(Mail v) { this.mail = v; }
}
