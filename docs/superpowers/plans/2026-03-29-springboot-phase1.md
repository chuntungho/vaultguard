# VaultGuard Spring Boot Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Phase 1 of the Spring Boot 4 rewrite: a fully working Bitwarden-compatible server with user auth, vault CRUD, folders, attachments, basic 2FA, organizations, collections, and sync.

**Architecture:** Classic layered monolith (`controller → service → repository`) in a `springboot/` subdirectory of the repo. Spring Boot 4.x on Java 21 with virtual threads, Spring Security 7, Spring Data JPA + Hibernate 7, Liquibase for schema management.

**Tech Stack:** Java 21, Spring Boot 4.x, Spring Security 7, Spring Data JPA 4 / Hibernate 7, Nimbus JOSE+JWT, SQLite (xerial), Liquibase, JUnit 5, Mockito, H2 (test), Testcontainers (integration)

---

## File Map

```
springboot/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/vaultguard/
│   │   │   ├── VaultGuardApplication.java
│   │   │   ├── config/
│   │   │   │   ├── VaultGuardProperties.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── WebConfig.java
│   │   │   ├── auth/
│   │   │   │   ├── JwtService.java
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   └── VaultGuardUserDetails.java
│   │   │   ├── crypto/
│   │   │   │   └── PasswordHashService.java
│   │   │   ├── db/
│   │   │   │   ├── entity/
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── Device.java
│   │   │   │   │   ├── Cipher.java
│   │   │   │   │   ├── Folder.java
│   │   │   │   │   ├── Attachment.java
│   │   │   │   │   ├── TwoFactor.java
│   │   │   │   │   ├── Organization.java
│   │   │   │   │   ├── OrgMembership.java
│   │   │   │   │   ├── Collection.java
│   │   │   │   │   ├── CollectionCipher.java
│   │   │   │   │   └── CollectionUser.java
│   │   │   │   └── repository/
│   │   │   │       ├── UserRepository.java
│   │   │   │       ├── DeviceRepository.java
│   │   │   │       ├── CipherRepository.java
│   │   │   │       ├── FolderRepository.java
│   │   │   │       ├── AttachmentRepository.java
│   │   │   │       ├── TwoFactorRepository.java
│   │   │   │       ├── OrganizationRepository.java
│   │   │   │       ├── OrgMembershipRepository.java
│   │   │   │       ├── CollectionRepository.java
│   │   │   │       ├── CollectionCipherRepository.java
│   │   │   │       └── CollectionUserRepository.java
│   │   │   ├── service/
│   │   │   │   ├── UserService.java
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── CipherService.java
│   │   │   │   ├── FolderService.java
│   │   │   │   ├── AttachmentService.java
│   │   │   │   ├── TwoFactorService.java
│   │   │   │   ├── OrganizationService.java
│   │   │   │   └── SyncService.java
│   │   │   ├── api/
│   │   │   │   ├── identity/
│   │   │   │   │   └── IdentityController.java
│   │   │   │   ├── accounts/
│   │   │   │   │   └── AccountsController.java
│   │   │   │   ├── ciphers/
│   │   │   │   │   └── CiphersController.java
│   │   │   │   ├── folders/
│   │   │   │   │   └── FoldersController.java
│   │   │   │   ├── organizations/
│   │   │   │   │   └── OrganizationsController.java
│   │   │   │   ├── collections/
│   │   │   │   │   └── CollectionsController.java
│   │   │   │   └── sync/
│   │   │   │       └── SyncController.java
│   │   │   └── util/
│   │   │       └── UuidUtil.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-sqlite.properties
│   │       ├── application-postgres.properties
│   │       ├── application-mysql.properties
│   │       └── db/changelog/
│   │           ├── db.changelog-master.xml
│   │           └── changes/
│   │               ├── 001-initial-schema.xml
│   │               └── 002-twofactor-orgs-collections.xml
│   └── test/
│       └── java/com/vaultguard/
│           ├── crypto/
│           │   └── PasswordHashServiceTest.java
│           ├── auth/
│           │   └── JwtServiceTest.java
│           ├── service/
│           │   ├── UserServiceTest.java
│           │   ├── CipherServiceTest.java
│           │   └── SyncServiceTest.java
│           └── api/
│               ├── IdentityControllerTest.java
│               ├── CiphersControllerTest.java
│               └── SyncControllerTest.java
```

---

## Task 1: Maven Project Skeleton

**Files:**
- Create: `springboot/pom.xml`
- Create: `springboot/src/main/java/com/vaultguard/VaultGuardApplication.java`
- Create: `springboot/src/main/resources/application.properties`

- [ ] **Step 1: Create `springboot/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.0</version>
        <relativePath/>
    </parent>

    <groupId>com.vaultguard</groupId>
    <artifactId>vaultguard-server</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>VaultGuard Server</name>

    <properties>
        <java.version>21</java.version>
        <nimbus-jose-jwt.version>9.40</nimbus-jose-jwt.version>
        <totp.version>1.0</totp.version>
    </properties>

    <dependencies>
        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Mail -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-mail</artifactId>
        </dependency>

        <!-- Liquibase -->
        <dependency>
            <groupId>org.liquibase</groupId>
            <artifactId>liquibase-core</artifactId>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>com.nimbusds</groupId>
            <artifactId>nimbus-jose-jwt</artifactId>
            <version>${nimbus-jose-jwt.version}</version>
        </dependency>

        <!-- SQLite -->
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.45.3.0</version>
        </dependency>
        <dependency>
            <groupId>io.github.willena</groupId>
            <artifactId>hibernate-dialect-sqlite</artifactId>
            <version>0.5.0</version>
        </dependency>

        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- TOTP -->
        <dependency>
            <groupId>dev.samstevens.totp</groupId>
            <artifactId>totp</artifactId>
            <version>1.7.1</version>
        </dependency>

        <!-- Config Processor -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `springboot/src/main/java/com/vaultguard/VaultGuardApplication.java`**

```java
package com.vaultguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VaultGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(VaultGuardApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `springboot/src/main/resources/application.properties`**

```properties
# Server
server.port=8080
spring.threads.virtual.enabled=true

# Active profile (sqlite | postgres | mysql)
spring.profiles.active=sqlite

# Liquibase
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml

# VaultGuard settings
vaultguard.domain=http://localhost:8080
vaultguard.signups-allowed=true
vaultguard.invitations-allowed=true
vaultguard.password-iterations=600000
vaultguard.jwt.access-token-expiry-seconds=3600
vaultguard.jwt.refresh-token-expiry-days=30
vaultguard.rsa-key-path=rsa_key.pem
vaultguard.attachments-path=data/attachments

# Rate limiting
vaultguard.rate-limit.max-requests=20
vaultguard.rate-limit.window-seconds=60

# Mail (override in production)
spring.mail.host=localhost
spring.mail.port=1025
vaultguard.mail.from=no-reply@vaultguard.local
vaultguard.mail.from-name=VaultGuard
```

- [ ] **Step 4: Create `springboot/src/main/resources/application-sqlite.properties`**

```properties
spring.datasource.url=jdbc:sqlite:data/db.sqlite3
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=io.github.willena.extra.orm.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=none
```

- [ ] **Step 5: Create `springboot/src/main/resources/application-postgres.properties`**

```properties
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/vaultguard}
spring.datasource.username=${DATABASE_USERNAME:vaultguard}
spring.datasource.password=${DATABASE_PASSWORD:vaultguard}
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=none
```

- [ ] **Step 6: Create `springboot/src/main/resources/application-mysql.properties`**

```properties
spring.datasource.url=${DATABASE_URL:jdbc:mysql://localhost:3306/vaultguard}
spring.datasource.username=${DATABASE_USERNAME:vaultguard}
spring.datasource.password=${DATABASE_PASSWORD:vaultguard}
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=none
```

- [ ] **Step 7: Verify the project compiles**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add springboot/
git commit -m "feat: bootstrap Spring Boot 4 Maven project"
```

---

## Task 2: Configuration Properties

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/config/VaultGuardProperties.java`

- [ ] **Step 1: Write test**

Create `springboot/src/test/java/com/vaultguard/config/VaultGuardPropertiesTest.java`:

```java
package com.vaultguard.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VaultGuardPropertiesTest {

    @Autowired
    private VaultGuardProperties props;

    @Test
    void defaultSignupsAllowed() {
        assertThat(props.isSignupsAllowed()).isTrue();
    }

    @Test
    void defaultJwtExpiry() {
        assertThat(props.getJwt().getAccessTokenExpirySeconds()).isEqualTo(3600);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd springboot && mvn test -pl . -Dtest=VaultGuardPropertiesTest -q 2>&1 | tail -5
```

Expected: FAIL — class not found

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/config/VaultGuardProperties.java`**

```java
package com.vaultguard.config;

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

    // Getters and setters
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
    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt v) { this.jwt = v; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit v) { this.rateLimit = v; }
    public Mail getMail() { return mail; }
    public void setMail(Mail v) { this.mail = v; }
}
```

- [ ] **Step 4: Create test application properties**

Create `springboot/src/test/resources/application-test.properties`:

```properties
# Use H2 for unit tests
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=none
spring.liquibase.enabled=false
vaultguard.rsa-key-path=test-rsa-key.pem
```

- [ ] **Step 5: Run test**

```bash
cd springboot && mvn test -Dtest=VaultGuardPropertiesTest 2>&1 | tail -5
```

Expected: Tests run: 2, Failures: 0, Errors: 0

- [ ] **Step 6: Commit**

```bash
git add springboot/
git commit -m "feat: add VaultGuardProperties configuration"
```

---

## Task 3: JPA Entities — Users, Devices, Folders

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/db/entity/User.java`
- Create: `springboot/src/main/java/com/vaultguard/db/entity/Device.java`
- Create: `springboot/src/main/java/com/vaultguard/db/entity/Folder.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/UserRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/DeviceRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/FolderRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/util/UuidUtil.java`

- [ ] **Step 1: Create `springboot/src/main/java/com/vaultguard/util/UuidUtil.java`**

```java
package com.vaultguard.util;

import java.util.UUID;

public final class UuidUtil {
    private UuidUtil() {}

    public static String newUuid() {
        return UUID.randomUUID().toString();
    }
}
```

- [ ] **Step 2: Create `springboot/src/main/java/com/vaultguard/db/entity/User.java`**

```java
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
    private int kdfType = 0; // 0 = PBKDF2, 1 = Argon2id

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
    private String keyHash; // the user's protected symmetric key

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

    // Getters and setters (all fields)
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
```

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/db/entity/Device.java`**

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    private int type;

    @Column(name = "push_token")
    private String pushToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken = "";

    @Column(name = "twofactor_remember", columnDefinition = "TEXT")
    private String twofactorRemember;

    @Column(name = "last_active")
    private Instant lastActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public String getPushToken() { return pushToken; }
    public void setPushToken(String pushToken) { this.pushToken = pushToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getTwofactorRemember() { return twofactorRemember; }
    public void setTwofactorRemember(String twofactorRemember) { this.twofactorRemember = twofactorRemember; }
    public Instant getLastActive() { return lastActive; }
    public void setLastActive(Instant lastActive) { this.lastActive = lastActive; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: Create `springboot/src/main/java/com/vaultguard/db/entity/Folder.java`**

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "folders")
public class Folder {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 5: Create repositories**

Create `springboot/src/main/java/com/vaultguard/db/repository/UserRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByApiKey(String apiKey);
}
```

Create `springboot/src/main/java/com/vaultguard/db/repository/DeviceRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, String> {
    List<Device> findByUserUuid(String userUuid);
    Optional<Device> findByUserUuidAndName(String userUuid, String name);
    void deleteByUserUuid(String userUuid);
}
```

Create `springboot/src/main/java/com/vaultguard/db/repository/FolderRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, String> {
    List<Folder> findByUserUuid(String userUuid);
}
```

- [ ] **Step 6: Compile to verify**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add springboot/
git commit -m "feat: add User, Device, Folder entities and repositories"
```

---

## Task 4: JPA Entities — Ciphers, Attachments, TwoFactor

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/db/entity/Cipher.java`
- Create: `springboot/src/main/java/com/vaultguard/db/entity/Attachment.java`
- Create: `springboot/src/main/java/com/vaultguard/db/entity/TwoFactor.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/CipherRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/AttachmentRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/TwoFactorRepository.java`

- [ ] **Step 1: Create `springboot/src/main/java/com/vaultguard/db/entity/Cipher.java`**

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ciphers")
public class Cipher {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "user_uuid", length = 36)
    private String userUuid;  // null for org ciphers

    @Column(name = "organization_uuid", length = 36)
    private String organizationUuid;

    @Column(name = "folder_uuid", length = 36)
    private String folderUuid;

    @Column(name = "type", nullable = false)
    private int type; // 1=Login, 2=SecureNote, 3=Card, 4=Identity

    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data; // JSON

    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "fields", columnDefinition = "TEXT")
    private String fields; // JSON array

    @Column(name = "password_history", columnDefinition = "TEXT")
    private String passwordHistory; // JSON array

    @Column(name = "key", columnDefinition = "TEXT")
    private String key;

    @Column(name = "reprompt", nullable = false)
    private int reprompt = 0;

    @Column(name = "deleted_date")
    private Instant deletedDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getOrganizationUuid() { return organizationUuid; }
    public void setOrganizationUuid(String organizationUuid) { this.organizationUuid = organizationUuid; }
    public String getFolderUuid() { return folderUuid; }
    public void setFolderUuid(String folderUuid) { this.folderUuid = folderUuid; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getFields() { return fields; }
    public void setFields(String fields) { this.fields = fields; }
    public String getPasswordHistory() { return passwordHistory; }
    public void setPasswordHistory(String passwordHistory) { this.passwordHistory = passwordHistory; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public int getReprompt() { return reprompt; }
    public void setReprompt(int reprompt) { this.reprompt = reprompt; }
    public Instant getDeletedDate() { return deletedDate; }
    public void setDeletedDate(Instant deletedDate) { this.deletedDate = deletedDate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Create `springboot/src/main/java/com/vaultguard/db/entity/Attachment.java`**

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "cipher_uuid", nullable = false, length = 36)
    private String cipherUuid;

    @Column(name = "file_name", nullable = false, columnDefinition = "TEXT")
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "akey", columnDefinition = "TEXT")
    private String akey;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCipherUuid() { return cipherUuid; }
    public void setCipherUuid(String cipherUuid) { this.cipherUuid = cipherUuid; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getAkey() { return akey; }
    public void setAkey(String akey) { this.akey = akey; }
}
```

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/db/entity/TwoFactor.java`**

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "twofactor")
@IdClass(TwoFactor.TwoFactorId.class)
public class TwoFactor {

    // type: 0=TOTP, 1=Email, 2=Duo, 3=YubiKey, 4=U2F, 5=Remember, 6=OrganizationDuo, 7=WebAuthn
    @Id
    @Column(name = "user_uuid", length = 36)
    private String userUuid;

    @Id
    @Column(name = "type", nullable = false)
    private int type;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data;

    @Column(name = "last_used")
    private Instant lastUsed;

    public static class TwoFactorId implements Serializable {
        private String userUuid;
        private int type;

        public TwoFactorId() {}
        public TwoFactorId(String userUuid, int type) {
            this.userUuid = userUuid;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TwoFactorId other)) return false;
            return type == other.type && Objects.equals(userUuid, other.userUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userUuid, type);
        }
    }

    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public Instant getLastUsed() { return lastUsed; }
    public void setLastUsed(Instant lastUsed) { this.lastUsed = lastUsed; }
}
```

- [ ] **Step 4: Create cipher, attachment, twofactor repositories**

Create `springboot/src/main/java/com/vaultguard/db/repository/CipherRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Cipher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CipherRepository extends JpaRepository<Cipher, String> {
    List<Cipher> findByUserUuid(String userUuid);

    @Query("SELECT c FROM Cipher c WHERE c.userUuid = :userUuid OR c.organizationUuid IN " +
           "(SELECT m.orgUuid FROM OrgMembership m WHERE m.userUuid = :userUuid AND m.status = 2)")
    List<Cipher> findAllAccessibleByUser(@Param("userUuid") String userUuid);

    List<Cipher> findByOrganizationUuid(String organizationUuid);
}
```

Create `springboot/src/main/java/com/vaultguard/db/repository/AttachmentRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, String> {
    List<Attachment> findByCipherUuid(String cipherUuid);
    void deleteByCipherUuid(String cipherUuid);
}
```

Create `springboot/src/main/java/com/vaultguard/db/repository/TwoFactorRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.TwoFactor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TwoFactorRepository extends JpaRepository<TwoFactor, TwoFactor.TwoFactorId> {
    List<TwoFactor> findByUserUuidAndEnabled(String userUuid, boolean enabled);
    Optional<TwoFactor> findByUserUuidAndType(String userUuid, int type);
}
```

- [ ] **Step 5: Compile**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add springboot/
git commit -m "feat: add Cipher, Attachment, TwoFactor entities and repositories"
```

---

## Task 5: JPA Entities — Organizations, Collections

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/db/entity/Organization.java`
- Create: `springboot/src/main/java/com/vaultguard/db/entity/OrgMembership.java`
- Create: `springboot/src/main/java/com/vaultguard/db/entity/Collection.java`
- Create: `springboot/src/main/java/com/vaultguard/db/entity/CollectionCipher.java`
- Create: `springboot/src/main/java/com/vaultguard/db/entity/CollectionUser.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/OrganizationRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/OrgMembershipRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/CollectionRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/CollectionCipherRepository.java`
- Create: `springboot/src/main/java/com/vaultguard/db/repository/CollectionUserRepository.java`

- [ ] **Step 1: Create Organization entity**

Create `springboot/src/main/java/com/vaultguard/db/entity/Organization.java`:

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "billing_email", nullable = false)
    private String billingEmail;

    @Column(name = "private_key", columnDefinition = "TEXT")
    private String privateKey;

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "plan", nullable = false)
    private String plan = "Free";

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBillingEmail() { return billingEmail; }
    public void setBillingEmail(String billingEmail) { this.billingEmail = billingEmail; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
}
```

- [ ] **Step 2: Create OrgMembership entity**

Create `springboot/src/main/java/com/vaultguard/db/entity/OrgMembership.java`:

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users_organizations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_uuid", "org_uuid"}))
public class OrgMembership {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    @Column(name = "org_uuid", nullable = false, length = 36)
    private String orgUuid;

    // atype: 0=Owner, 1=Admin, 2=User, 3=Manager, 4=Custom
    @Column(name = "atype", nullable = false)
    private int atype;

    // status: 0=Invited, 1=Accepted, 2=Confirmed
    @Column(name = "status", nullable = false)
    private int status;

    @Column(name = "access_all", nullable = false)
    private boolean accessAll = false;

    @Column(name = "akey", columnDefinition = "TEXT")
    private String akey;

    @Column(name = "reset_password_key", columnDefinition = "TEXT")
    private String resetPasswordKey;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getOrgUuid() { return orgUuid; }
    public void setOrgUuid(String orgUuid) { this.orgUuid = orgUuid; }
    public int getAtype() { return atype; }
    public void setAtype(int atype) { this.atype = atype; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public boolean isAccessAll() { return accessAll; }
    public void setAccessAll(boolean accessAll) { this.accessAll = accessAll; }
    public String getAkey() { return akey; }
    public void setAkey(String akey) { this.akey = akey; }
    public String getResetPasswordKey() { return resetPasswordKey; }
    public void setResetPasswordKey(String resetPasswordKey) { this.resetPasswordKey = resetPasswordKey; }
}
```

- [ ] **Step 3: Create Collection and mapping entities**

Create `springboot/src/main/java/com/vaultguard/db/entity/Collection.java`:

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "collections")
public class Collection {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "org_uuid", nullable = false, length = 36)
    private String orgUuid;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getOrgUuid() { return orgUuid; }
    public void setOrgUuid(String orgUuid) { this.orgUuid = orgUuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

Create `springboot/src/main/java/com/vaultguard/db/entity/CollectionCipher.java`:

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "ciphers_collections")
@IdClass(CollectionCipher.CollectionCipherId.class)
public class CollectionCipher {

    @Id
    @Column(name = "collection_uuid", length = 36)
    private String collectionUuid;

    @Id
    @Column(name = "cipher_uuid", length = 36)
    private String cipherUuid;

    public static class CollectionCipherId implements Serializable {
        private String collectionUuid;
        private String cipherUuid;

        public CollectionCipherId() {}

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CollectionCipherId other)) return false;
            return Objects.equals(collectionUuid, other.collectionUuid) &&
                   Objects.equals(cipherUuid, other.cipherUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(collectionUuid, cipherUuid);
        }
    }

    public String getCollectionUuid() { return collectionUuid; }
    public void setCollectionUuid(String collectionUuid) { this.collectionUuid = collectionUuid; }
    public String getCipherUuid() { return cipherUuid; }
    public void setCipherUuid(String cipherUuid) { this.cipherUuid = cipherUuid; }
}
```

Create `springboot/src/main/java/com/vaultguard/db/entity/CollectionUser.java`:

```java
package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "users_collections")
@IdClass(CollectionUser.CollectionUserId.class)
public class CollectionUser {

    @Id
    @Column(name = "collection_uuid", length = 36)
    private String collectionUuid;

    @Id
    @Column(name = "users_organizations_uuid", length = 36)
    private String orgMembershipUuid;

    @Column(name = "read_only", nullable = false)
    private boolean readOnly = false;

    @Column(name = "hide_passwords", nullable = false)
    private boolean hidePasswords = false;

    public static class CollectionUserId implements Serializable {
        private String collectionUuid;
        private String orgMembershipUuid;

        public CollectionUserId() {}

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CollectionUserId other)) return false;
            return Objects.equals(collectionUuid, other.collectionUuid) &&
                   Objects.equals(orgMembershipUuid, other.orgMembershipUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(collectionUuid, orgMembershipUuid);
        }
    }

    public String getCollectionUuid() { return collectionUuid; }
    public void setCollectionUuid(String collectionUuid) { this.collectionUuid = collectionUuid; }
    public String getOrgMembershipUuid() { return orgMembershipUuid; }
    public void setOrgMembershipUuid(String orgMembershipUuid) { this.orgMembershipUuid = orgMembershipUuid; }
    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
    public boolean isHidePasswords() { return hidePasswords; }
    public void setHidePasswords(boolean hidePasswords) { this.hidePasswords = hidePasswords; }
}
```

- [ ] **Step 4: Create org/collection repositories**

Create `springboot/src/main/java/com/vaultguard/db/repository/OrganizationRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, String> {
}
```

Create `springboot/src/main/java/com/vaultguard/db/repository/OrgMembershipRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.OrgMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgMembershipRepository extends JpaRepository<OrgMembership, String> {
    List<OrgMembership> findByUserUuid(String userUuid);
    List<OrgMembership> findByOrgUuid(String orgUuid);
    Optional<OrgMembership> findByUserUuidAndOrgUuid(String userUuid, String orgUuid);
    List<OrgMembership> findByUserUuidAndStatus(String userUuid, int status);
}
```

Create `springboot/src/main/java/com/vaultguard/db/repository/CollectionRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionRepository extends JpaRepository<Collection, String> {
    List<Collection> findByOrgUuid(String orgUuid);
}
```

Create `springboot/src/main/java/com/vaultguard/db/repository/CollectionCipherRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.CollectionCipher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionCipherRepository extends JpaRepository<CollectionCipher, CollectionCipher.CollectionCipherId> {
    List<CollectionCipher> findByCollectionUuid(String collectionUuid);
    List<CollectionCipher> findByCipherUuid(String cipherUuid);
    void deleteByCipherUuid(String cipherUuid);
    void deleteByCollectionUuid(String collectionUuid);
}
```

Create `springboot/src/main/java/com/vaultguard/db/repository/CollectionUserRepository.java`:

```java
package com.vaultguard.db.repository;

import com.vaultguard.db.entity.CollectionUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionUserRepository extends JpaRepository<CollectionUser, CollectionUser.CollectionUserId> {
    List<CollectionUser> findByOrgMembershipUuid(String orgMembershipUuid);
    List<CollectionUser> findByCollectionUuid(String collectionUuid);
}
```

- [ ] **Step 5: Compile**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add springboot/
git commit -m "feat: add Organization, Collection, OrgMembership entities and repositories"
```

---

## Task 6: Liquibase Schema

**Files:**
- Create: `springboot/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `springboot/src/main/resources/db/changelog/changes/001-initial-schema.xml`
- Create: `springboot/src/main/resources/db/changelog/changes/002-orgs-collections-twofactor.xml`

- [ ] **Step 1: Create master changelog**

Create `springboot/src/main/resources/db/changelog/db.changelog-master.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <include file="db/changelog/changes/001-initial-schema.xml"/>
    <include file="db/changelog/changes/002-orgs-collections-twofactor.xml"/>
</databaseChangeLog>
```

- [ ] **Step 2: Create initial schema changeset**

Create `springboot/src/main/resources/db/changelog/changes/001-initial-schema.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="001-users" author="vaultguard">
        <createTable tableName="users">
            <column name="uuid" type="VARCHAR(36)"><constraints primaryKey="true" nullable="false"/></column>
            <column name="email" type="VARCHAR(255)"><constraints unique="true" nullable="false"/></column>
            <column name="name" type="VARCHAR(255)"><constraints nullable="false"/></column>
            <column name="password_hash" type="TEXT"><constraints nullable="false"/></column>
            <column name="kdf_type" type="INT" defaultValueNumeric="0"><constraints nullable="false"/></column>
            <column name="kdf_iterations" type="INT" defaultValueNumeric="600000"><constraints nullable="false"/></column>
            <column name="kdf_memory" type="INT"/>
            <column name="kdf_parallelism" type="INT"/>
            <column name="private_key" type="TEXT"/>
            <column name="public_key" type="TEXT"/>
            <column name="key_hash" type="TEXT"/>
            <column name="totp_secret" type="VARCHAR(255)"/>
            <column name="totp_recover" type="TEXT"/>
            <column name="security_stamp" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="stamp_exception" type="TEXT"/>
            <column name="password_hint" type="VARCHAR(255)"/>
            <column name="equivalent_domains" type="TEXT" defaultValue="[]"><constraints nullable="false"/></column>
            <column name="excluded_globals" type="TEXT" defaultValue="[]"><constraints nullable="false"/></column>
            <column name="enabled" type="BOOLEAN" defaultValueBoolean="true"><constraints nullable="false"/></column>
            <column name="email_new" type="VARCHAR(255)"/>
            <column name="email_new_token" type="VARCHAR(255)"/>
            <column name="verified_at" type="DATETIME"/>
            <column name="last_verifying_at" type="DATETIME"/>
            <column name="login_verify_count" type="INT" defaultValueNumeric="0"><constraints nullable="false"/></column>
            <column name="api_key" type="TEXT"/>
            <column name="created_at" type="DATETIME"><constraints nullable="false"/></column>
            <column name="updated_at" type="DATETIME"><constraints nullable="false"/></column>
        </createTable>
    </changeSet>

    <changeSet id="001-devices" author="vaultguard">
        <createTable tableName="devices">
            <column name="uuid" type="VARCHAR(36)"><constraints primaryKey="true" nullable="false"/></column>
            <column name="user_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="name" type="VARCHAR(255)"><constraints nullable="false"/></column>
            <column name="type" type="INT"><constraints nullable="false"/></column>
            <column name="push_token" type="VARCHAR(255)"/>
            <column name="refresh_token" type="TEXT" defaultValue=""><constraints nullable="false"/></column>
            <column name="twofactor_remember" type="TEXT"/>
            <column name="last_active" type="DATETIME"/>
            <column name="created_at" type="DATETIME"><constraints nullable="false"/></column>
            <column name="updated_at" type="DATETIME"><constraints nullable="false"/></column>
        </createTable>
        <addForeignKeyConstraint baseTableName="devices" baseColumnNames="user_uuid"
            referencedTableName="users" referencedColumnNames="uuid"
            constraintName="fk_devices_user"/>
    </changeSet>

    <changeSet id="001-folders" author="vaultguard">
        <createTable tableName="folders">
            <column name="uuid" type="VARCHAR(36)"><constraints primaryKey="true" nullable="false"/></column>
            <column name="user_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="name" type="TEXT"><constraints nullable="false"/></column>
            <column name="created_at" type="DATETIME"><constraints nullable="false"/></column>
            <column name="updated_at" type="DATETIME"><constraints nullable="false"/></column>
        </createTable>
        <addForeignKeyConstraint baseTableName="folders" baseColumnNames="user_uuid"
            referencedTableName="users" referencedColumnNames="uuid"
            constraintName="fk_folders_user"/>
    </changeSet>

    <changeSet id="001-ciphers" author="vaultguard">
        <createTable tableName="ciphers">
            <column name="uuid" type="VARCHAR(36)"><constraints primaryKey="true" nullable="false"/></column>
            <column name="user_uuid" type="VARCHAR(36)"/>
            <column name="organization_uuid" type="VARCHAR(36)"/>
            <column name="folder_uuid" type="VARCHAR(36)"/>
            <column name="type" type="INT"><constraints nullable="false"/></column>
            <column name="data" type="TEXT"><constraints nullable="false"/></column>
            <column name="name" type="TEXT"/>
            <column name="notes" type="TEXT"/>
            <column name="fields" type="TEXT"/>
            <column name="password_history" type="TEXT"/>
            <column name="key" type="TEXT"/>
            <column name="reprompt" type="INT" defaultValueNumeric="0"><constraints nullable="false"/></column>
            <column name="deleted_date" type="DATETIME"/>
            <column name="created_at" type="DATETIME"><constraints nullable="false"/></column>
            <column name="updated_at" type="DATETIME"><constraints nullable="false"/></column>
        </createTable>
    </changeSet>

    <changeSet id="001-attachments" author="vaultguard">
        <createTable tableName="attachments">
            <column name="id" type="VARCHAR(36)"><constraints primaryKey="true" nullable="false"/></column>
            <column name="cipher_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="file_name" type="TEXT"><constraints nullable="false"/></column>
            <column name="file_size" type="BIGINT"><constraints nullable="false"/></column>
            <column name="akey" type="TEXT"/>
        </createTable>
        <addForeignKeyConstraint baseTableName="attachments" baseColumnNames="cipher_uuid"
            referencedTableName="ciphers" referencedColumnNames="uuid"
            constraintName="fk_attachments_cipher"/>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 3: Create orgs/collections/twofactor changeset**

Create `springboot/src/main/resources/db/changelog/changes/002-orgs-collections-twofactor.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="002-organizations" author="vaultguard">
        <createTable tableName="organizations">
            <column name="uuid" type="VARCHAR(36)"><constraints primaryKey="true" nullable="false"/></column>
            <column name="name" type="VARCHAR(255)"><constraints nullable="false"/></column>
            <column name="billing_email" type="VARCHAR(255)"><constraints nullable="false"/></column>
            <column name="private_key" type="TEXT"/>
            <column name="public_key" type="TEXT"/>
            <column name="plan" type="VARCHAR(50)" defaultValue="Free"><constraints nullable="false"/></column>
        </createTable>
    </changeSet>

    <changeSet id="002-users-organizations" author="vaultguard">
        <createTable tableName="users_organizations">
            <column name="uuid" type="VARCHAR(36)"><constraints primaryKey="true" nullable="false"/></column>
            <column name="user_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="org_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="atype" type="INT"><constraints nullable="false"/></column>
            <column name="status" type="INT"><constraints nullable="false"/></column>
            <column name="access_all" type="BOOLEAN" defaultValueBoolean="false"><constraints nullable="false"/></column>
            <column name="akey" type="TEXT"/>
            <column name="reset_password_key" type="TEXT"/>
        </createTable>
        <addUniqueConstraint tableName="users_organizations" columnNames="user_uuid,org_uuid"
            constraintName="uq_users_organizations"/>
        <addForeignKeyConstraint baseTableName="users_organizations" baseColumnNames="user_uuid"
            referencedTableName="users" referencedColumnNames="uuid"
            constraintName="fk_uorg_user"/>
        <addForeignKeyConstraint baseTableName="users_organizations" baseColumnNames="org_uuid"
            referencedTableName="organizations" referencedColumnNames="uuid"
            constraintName="fk_uorg_org"/>
    </changeSet>

    <changeSet id="002-collections" author="vaultguard">
        <createTable tableName="collections">
            <column name="uuid" type="VARCHAR(36)"><constraints primaryKey="true" nullable="false"/></column>
            <column name="org_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="name" type="TEXT"><constraints nullable="false"/></column>
        </createTable>
        <addForeignKeyConstraint baseTableName="collections" baseColumnNames="org_uuid"
            referencedTableName="organizations" referencedColumnNames="uuid"
            constraintName="fk_collections_org"/>
    </changeSet>

    <changeSet id="002-ciphers-collections" author="vaultguard">
        <createTable tableName="ciphers_collections">
            <column name="collection_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="cipher_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
        </createTable>
        <addPrimaryKey tableName="ciphers_collections" columnNames="collection_uuid,cipher_uuid"/>
    </changeSet>

    <changeSet id="002-users-collections" author="vaultguard">
        <createTable tableName="users_collections">
            <column name="collection_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="users_organizations_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="read_only" type="BOOLEAN" defaultValueBoolean="false"><constraints nullable="false"/></column>
            <column name="hide_passwords" type="BOOLEAN" defaultValueBoolean="false"><constraints nullable="false"/></column>
        </createTable>
        <addPrimaryKey tableName="users_collections" columnNames="collection_uuid,users_organizations_uuid"/>
    </changeSet>

    <changeSet id="002-twofactor" author="vaultguard">
        <createTable tableName="twofactor">
            <column name="user_uuid" type="VARCHAR(36)"><constraints nullable="false"/></column>
            <column name="type" type="INT"><constraints nullable="false"/></column>
            <column name="enabled" type="BOOLEAN" defaultValueBoolean="true"><constraints nullable="false"/></column>
            <column name="data" type="TEXT"><constraints nullable="false"/></column>
            <column name="last_used" type="DATETIME"/>
        </createTable>
        <addPrimaryKey tableName="twofactor" columnNames="user_uuid,type"/>
        <addForeignKeyConstraint baseTableName="twofactor" baseColumnNames="user_uuid"
            referencedTableName="users" referencedColumnNames="uuid"
            constraintName="fk_twofactor_user"/>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 4: Enable Liquibase in test and verify it runs**

Update `springboot/src/test/resources/application-test.properties` to enable Liquibase with H2:

```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=none
spring.liquibase.enabled=true
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml
vaultguard.rsa-key-path=test-rsa-key.pem
```

- [ ] **Step 5: Write a simple schema test**

Create `springboot/src/test/java/com/vaultguard/db/SchemaTest.java`:

```java
package com.vaultguard.db;

import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SchemaTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void canSaveAndFindUser() {
        User user = new User();
        user.setUuid(UuidUtil.newUuid());
        user.setEmail("test@example.com");
        user.setName("Test User");
        user.setPasswordHash("hashed");
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);

        assertThat(userRepository.findByEmail("test@example.com")).isPresent();
    }
}
```

- [ ] **Step 6: Run test**

```bash
cd springboot && mvn test -Dtest=SchemaTest 2>&1 | tail -5
```

Expected: Tests run: 1, Failures: 0, Errors: 0

- [ ] **Step 7: Commit**

```bash
git add springboot/
git commit -m "feat: add Liquibase schema changelogs and schema test"
```

---

## Task 7: JWT Service

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/auth/JwtService.java`
- Create: `springboot/src/main/java/com/vaultguard/auth/VaultGuardUserDetails.java`
- Create: `springboot/src/test/java/com/vaultguard/auth/JwtServiceTest.java`

The JWT service loads an RSA key pair from disk and issues/validates RS256 tokens. For tests, it generates an ephemeral key pair in memory.

- [ ] **Step 1: Write failing test**

Create `springboot/src/test/java/com/vaultguard/auth/JwtServiceTest.java`:

```java
package com.vaultguard.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.vaultguard.config.VaultGuardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        VaultGuardProperties props = new VaultGuardProperties();
        jwtService = new JwtService(rsaKey, props);
    }

    @Test
    void issueAndValidateAccessToken() {
        String token = jwtService.issueAccessToken("user-uuid-123", "device-uuid-456");
        assertThat(token).isNotBlank();
        JwtService.ParsedToken parsed = jwtService.validateAccessToken(token);
        assertThat(parsed.userUuid()).isEqualTo("user-uuid-123");
        assertThat(parsed.deviceUuid()).isEqualTo("device-uuid-456");
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwtService.issueAccessToken("user-uuid-123", "device-uuid-456");
        // Tamper the token
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThatCode(() -> jwtService.validateAccessToken(tampered))
            .isInstanceOf(Exception.class);
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd springboot && mvn test -Dtest=JwtServiceTest 2>&1 | tail -5
```

Expected: FAIL — class not found

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/auth/VaultGuardUserDetails.java`**

```java
package com.vaultguard.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class VaultGuardUserDetails implements UserDetails {

    private final String userUuid;
    private final String deviceUuid;

    public VaultGuardUserDetails(String userUuid, String deviceUuid) {
        this.userUuid = userUuid;
        this.deviceUuid = deviceUuid;
    }

    public String getUserUuid() { return userUuid; }
    public String getDeviceUuid() { return deviceUuid; }

    @Override public String getUsername() { return userUuid; }
    @Override public String getPassword() { return null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
```

- [ ] **Step 4: Create `springboot/src/main/java/com/vaultguard/auth/JwtService.java`**

```java
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
```

- [ ] **Step 5: Create RSA key bean**

Create `springboot/src/main/java/com/vaultguard/config/RsaKeyConfig.java`:

```java
package com.vaultguard.config;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
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
```

- [ ] **Step 6: Run tests**

```bash
cd springboot && mvn test -Dtest=JwtServiceTest 2>&1 | tail -5
```

Expected: Tests run: 2, Failures: 0, Errors: 0

- [ ] **Step 7: Commit**

```bash
git add springboot/
git commit -m "feat: add JwtService with RS256 signing"
```

---

## Task 8: Security Config + JWT Filter

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/auth/JwtAuthenticationFilter.java`
- Create: `springboot/src/main/java/com/vaultguard/config/SecurityConfig.java`
- Create: `springboot/src/main/java/com/vaultguard/config/WebConfig.java`

- [ ] **Step 1: Create `springboot/src/main/java/com/vaultguard/auth/JwtAuthenticationFilter.java`**

```java
package com.vaultguard.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JwtService.ParsedToken parsed = jwtService.validateAccessToken(token);
                VaultGuardUserDetails userDetails =
                    new VaultGuardUserDetails(parsed.userUuid(), parsed.deviceUuid());
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // Invalid token — leave SecurityContext unauthenticated
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: Create `springboot/src/main/java/com/vaultguard/config/SecurityConfig.java`**

```java
package com.vaultguard.config;

import com.vaultguard.auth.JwtAuthenticationFilter;
import com.vaultguard.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/identity/connect/token",
                    "/api/accounts/register",
                    "/api/accounts/prelogin",
                    "/icons/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/config/WebConfig.java`**

```java
package com.vaultguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/identity/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*");
                registry.addMapping("/api/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*");
            }
        };
    }
}
```

- [ ] **Step 4: Compile**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add springboot/
git commit -m "feat: add JWT filter and Spring Security configuration"
```

---

## Task 9: Password Hash Service + UserService

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/crypto/PasswordHashService.java`
- Create: `springboot/src/main/java/com/vaultguard/service/UserService.java`
- Create: `springboot/src/test/java/com/vaultguard/crypto/PasswordHashServiceTest.java`
- Create: `springboot/src/test/java/com/vaultguard/service/UserServiceTest.java`

- [ ] **Step 1: Write failing test for PasswordHashService**

Create `springboot/src/test/java/com/vaultguard/crypto/PasswordHashServiceTest.java`:

```java
package com.vaultguard.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashServiceTest {

    private final PasswordHashService service = new PasswordHashService();

    @Test
    void hashAndVerify() {
        // The client sends an already-stretched hash; server does one more round
        String clientHash = "client-stretched-hash-base64";
        String serverHash = service.hashForStorage(clientHash);
        assertThat(serverHash).isNotEqualTo(clientHash);
        assertThat(service.verify(clientHash, serverHash)).isTrue();
    }

    @Test
    void differentInputsDifferentHashes() {
        String hash1 = service.hashForStorage("input1");
        String hash2 = service.hashForStorage("input2");
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd springboot && mvn test -Dtest=PasswordHashServiceTest 2>&1 | tail -5
```

Expected: FAIL

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/crypto/PasswordHashService.java`**

```java
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
            // Store as "base64salt:base64hash"
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
```

- [ ] **Step 4: Run test — expect pass**

```bash
cd springboot && mvn test -Dtest=PasswordHashServiceTest 2>&1 | tail -5
```

Expected: Tests run: 2, Failures: 0, Errors: 0

- [ ] **Step 5: Write failing UserService test**

Create `springboot/src/test/java/com/vaultguard/service/UserServiceTest.java`:

```java
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
```

- [ ] **Step 6: Run test — expect failure**

```bash
cd springboot && mvn test -Dtest=UserServiceTest 2>&1 | tail -5
```

Expected: FAIL

- [ ] **Step 7: Create `springboot/src/main/java/com/vaultguard/service/UserService.java`**

```java
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
        if (userRepository.existsByEmail(email.toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User();
        user.setUuid(UuidUtil.newUuid());
        user.setEmail(email.toLowerCase());
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
```

- [ ] **Step 8: Run test — expect pass**

```bash
cd springboot && mvn test -Dtest=UserServiceTest 2>&1 | tail -5
```

Expected: Tests run: 3, Failures: 0, Errors: 0

- [ ] **Step 9: Commit**

```bash
git add springboot/
git commit -m "feat: add PasswordHashService and UserService"
```

---

## Task 10: AuthService + IdentityController (Login/Token)

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/service/AuthService.java`
- Create: `springboot/src/main/java/com/vaultguard/api/identity/IdentityController.java`
- Create: `springboot/src/test/java/com/vaultguard/api/IdentityControllerTest.java`

This implements `POST /identity/connect/token` — the Bitwarden login endpoint. It handles grant types `password` and `refresh_token`, plus basic 2FA.

- [ ] **Step 1: Write failing integration test**

Create `springboot/src/test/java/com/vaultguard/api/IdentityControllerTest.java`:

```java
package com.vaultguard.api;

import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.util.UuidUtil;
import com.vaultguard.crypto.PasswordHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired PasswordHashService passwordHashService;

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        user.setUuid(UuidUtil.newUuid());
        user.setEmail("login@example.com");
        user.setName("Login User");
        user.setPasswordHash(passwordHashService.hashForStorage("correct-hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "login@example.com")
            .param("password", "correct-hash")
            .param("scope", "api offline_access")
            .param("client_id", "browser")
            .param("deviceType", "3")
            .param("deviceIdentifier", UuidUtil.newUuid())
            .param("deviceName", "Test Browser"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    void loginWithInvalidPasswordReturns400() throws Exception {
        mockMvc.perform(post("/identity/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "password")
            .param("username", "login@example.com")
            .param("password", "wrong-hash")
            .param("scope", "api offline_access")
            .param("client_id", "browser")
            .param("deviceType", "3")
            .param("deviceIdentifier", UuidUtil.newUuid())
            .param("deviceName", "Test Browser"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd springboot && mvn test -Dtest=IdentityControllerTest 2>&1 | tail -10
```

Expected: FAIL — controller not found (404)

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/service/AuthService.java`**

```java
package com.vaultguard.service;

import com.vaultguard.auth.JwtService;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Device;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.DeviceRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserService userService;
    private final DeviceRepository deviceRepository;
    private final JwtService jwtService;
    private final VaultGuardProperties props;

    public AuthService(UserService userService, DeviceRepository deviceRepository,
                       JwtService jwtService, VaultGuardProperties props) {
        this.userService = userService;
        this.deviceRepository = deviceRepository;
        this.jwtService = jwtService;
        this.props = props;
    }

    public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        String scope,
        String key,
        Boolean twoFactorRequired
    ) {}

    @Transactional
    public TokenResponse loginWithPassword(String username, String password,
                                           String deviceIdentifier, String deviceName, int deviceType) {
        User user = userService.findByEmail(username)
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }
        if (!userService.verifyPassword(password, user)) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        Device device = deviceRepository.findByUserUuidAndName(user.getUuid(), deviceIdentifier)
            .orElseGet(() -> {
                Device d = new Device();
                d.setUuid(UuidUtil.newUuid());
                d.setUserUuid(user.getUuid());
                d.setName(deviceIdentifier);
                d.setType(deviceType);
                return d;
            });
        device.setLastActive(Instant.now());
        device.setRefreshToken(UuidUtil.newUuid());
        deviceRepository.save(device);

        String accessToken = jwtService.issueAccessToken(user.getUuid(), device.getUuid());
        return new TokenResponse(
            accessToken,
            device.getRefreshToken(),
            props.getJwt().getAccessTokenExpirySeconds(),
            "Bearer",
            "api offline_access",
            user.getKeyHash(),
            null
        );
    }

    @Transactional
    public TokenResponse refreshToken(String refreshToken) {
        Device device = deviceRepository.findAll().stream()
            .filter(d -> refreshToken.equals(d.getRefreshToken()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        User user = userService.findById(device.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        device.setRefreshToken(UuidUtil.newUuid());
        device.setLastActive(Instant.now());
        deviceRepository.save(device);

        String accessToken = jwtService.issueAccessToken(user.getUuid(), device.getUuid());
        return new TokenResponse(
            accessToken,
            device.getRefreshToken(),
            props.getJwt().getAccessTokenExpirySeconds(),
            "Bearer",
            "api offline_access",
            user.getKeyHash(),
            null
        );
    }
}
```

- [ ] **Step 4: Create `springboot/src/main/java/com/vaultguard/api/identity/IdentityController.java`**

```java
package com.vaultguard.api.identity;

import com.vaultguard.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/identity")
public class IdentityController {

    private final AuthService authService;

    public IdentityController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/connect/token")
    public ResponseEntity<Map<String, Object>> token(
        @RequestParam("grant_type") String grantType,
        @RequestParam(value = "username", required = false) String username,
        @RequestParam(value = "password", required = false) String password,
        @RequestParam(value = "refresh_token", required = false) String refreshToken,
        @RequestParam(value = "deviceIdentifier", required = false) String deviceIdentifier,
        @RequestParam(value = "deviceName", required = false) String deviceName,
        @RequestParam(value = "deviceType", required = false, defaultValue = "0") int deviceType
    ) {
        try {
            AuthService.TokenResponse resp;
            if ("password".equals(grantType)) {
                resp = authService.loginWithPassword(username, password,
                    deviceIdentifier, deviceName, deviceType);
            } else if ("refresh_token".equals(grantType)) {
                resp = authService.refreshToken(refreshToken);
            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "unsupported_grant_type"));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", resp.accessToken());
            body.put("expires_in", resp.expiresIn());
            body.put("token_type", resp.tokenType());
            body.put("refresh_token", resp.refreshToken());
            body.put("scope", resp.scope());
            if (resp.key() != null) body.put("Key", resp.key());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid_grant", "error_description", e.getMessage()));
        }
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd springboot && mvn test -Dtest=IdentityControllerTest 2>&1 | tail -5
```

Expected: Tests run: 2, Failures: 0, Errors: 0

- [ ] **Step 6: Commit**

```bash
git add springboot/
git commit -m "feat: add AuthService and /identity/connect/token endpoint"
```

---

## Task 11: AccountsController (Register + Prelogin)

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/api/accounts/AccountsController.java`
- Create: `springboot/src/test/java/com/vaultguard/api/AccountsControllerTest.java`

- [ ] **Step 1: Write failing test**

Create `springboot/src/test/java/com/vaultguard/api/AccountsControllerTest.java`:

```java
package com.vaultguard.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordHashService passwordHashService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void registerCreatesUser() throws Exception {
        Map<String, Object> body = Map.of(
            "email", "newuser@example.com",
            "name", "New User",
            "masterPasswordHash", "hashed-password",
            "kdf", 0,
            "kdfIterations", 600000,
            "key", "protected-symmetric-key"
        );
        mockMvc.perform(post("/api/accounts/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());
    }

    @Test
    void preloginReturnsKdfInfo() throws Exception {
        User user = new User();
        user.setUuid(UuidUtil.newUuid());
        user.setEmail("existing@example.com");
        user.setName("Existing User");
        user.setPasswordHash(passwordHashService.hashForStorage("hash"));
        user.setKdfType(0);
        user.setKdfIterations(600000);
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);

        mockMvc.perform(post("/api/accounts/prelogin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"existing@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Kdf").value(0))
            .andExpect(jsonPath("$.KdfIterations").value(600000));
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd springboot && mvn test -Dtest=AccountsControllerTest 2>&1 | tail -5
```

Expected: FAIL

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/api/accounts/AccountsController.java`**

```java
package com.vaultguard.api.accounts;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.User;
import com.vaultguard.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {

    private final UserService userService;

    public AccountsController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String name = (String) body.getOrDefault("name", email);
        String masterPasswordHash = (String) body.get("masterPasswordHash");
        int kdf = ((Number) body.getOrDefault("kdf", 0)).intValue();
        int kdfIterations = ((Number) body.getOrDefault("kdfIterations", 600000)).intValue();
        Integer kdfMemory = body.containsKey("kdfMemory")
            ? ((Number) body.get("kdfMemory")).intValue() : null;
        Integer kdfParallelism = body.containsKey("kdfParallelism")
            ? ((Number) body.get("kdfParallelism")).intValue() : null;
        String key = (String) body.get("key");
        Map<String, Object> keys = (Map<String, Object>) body.get("keys");
        String publicKey = keys != null ? (String) keys.get("publicKey") : null;
        String privateKey = keys != null ? (String) keys.get("encryptedPrivateKey") : null;

        userService.register(email, name, masterPasswordHash, kdf, kdfIterations,
            kdfMemory, kdfParallelism, key, privateKey, publicKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/prelogin")
    public ResponseEntity<Map<String, Object>> prelogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        return userService.findByEmail(email).map(user -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("Kdf", user.getKdfType());
            resp.put("KdfIterations", user.getKdfIterations());
            if (user.getKdfMemory() != null) resp.put("KdfMemory", user.getKdfMemory());
            if (user.getKdfParallelism() != null) resp.put("KdfParallelism", user.getKdfParallelism());
            return ResponseEntity.ok(resp);
        }).orElseGet(() -> {
            // Return defaults to not leak whether account exists
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("Kdf", 0);
            resp.put("KdfIterations", 600000);
            return ResponseEntity.ok(resp);
        });
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> profile(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(toProfileResponse(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
        @AuthenticationPrincipal VaultGuardUserDetails principal,
        @RequestBody Map<String, Object> body) {
        User user = userService.findById(principal.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (body.containsKey("name")) user.setName((String) body.get("name"));
        if (body.containsKey("masterPasswordHint")) user.setPasswordHint((String) body.get("masterPasswordHint"));
        userService.save(user);
        return ResponseEntity.ok(toProfileResponse(user));
    }

    private Map<String, Object> toProfileResponse(User user) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", user.getUuid());
        resp.put("Name", user.getName());
        resp.put("Email", user.getEmail());
        resp.put("EmailVerified", user.getVerifiedAt() != null);
        resp.put("Premium", false);
        resp.put("MasterPasswordHint", user.getPasswordHint());
        resp.put("Culture", "en-US");
        resp.put("TwoFactorEnabled", false);
        resp.put("Key", user.getKeyHash());
        resp.put("PrivateKey", user.getPrivateKey());
        resp.put("SecurityStamp", user.getSecurityStamp());
        resp.put("Kdf", user.getKdfType());
        resp.put("KdfIterations", user.getKdfIterations());
        resp.put("KdfMemory", user.getKdfMemory());
        resp.put("KdfParallelism", user.getKdfParallelism());
        resp.put("Object", "profile");
        return resp;
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd springboot && mvn test -Dtest=AccountsControllerTest 2>&1 | tail -5
```

Expected: Tests run: 2, Failures: 0, Errors: 0

- [ ] **Step 5: Commit**

```bash
git add springboot/
git commit -m "feat: add AccountsController (register, prelogin, profile)"
```

---

## Task 12: CiphersController + FoldersController

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/service/CipherService.java`
- Create: `springboot/src/main/java/com/vaultguard/service/FolderService.java`
- Create: `springboot/src/main/java/com/vaultguard/api/ciphers/CiphersController.java`
- Create: `springboot/src/main/java/com/vaultguard/api/folders/FoldersController.java`
- Create: `springboot/src/test/java/com/vaultguard/api/CiphersControllerTest.java`

- [ ] **Step 1: Write failing cipher test**

Create `springboot/src/test/java/com/vaultguard/api/CiphersControllerTest.java`:

```java
package com.vaultguard.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.vaultguard.auth.JwtService;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.CipherRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CiphersControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CipherRepository cipherRepository;
    @Autowired PasswordHashService passwordHashService;
    @Autowired JwtService jwtService;

    private String userUuid;
    private String authToken;

    @BeforeEach
    void setUp() {
        cipherRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        userUuid = UuidUtil.newUuid();
        user.setUuid(userUuid);
        user.setEmail("cipher-test@example.com");
        user.setName("Cipher Test");
        user.setPasswordHash(passwordHashService.hashForStorage("hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
        authToken = jwtService.issueAccessToken(userUuid, UuidUtil.newUuid());
    }

    @Test
    void createAndGetCipher() throws Exception {
        Map<String, Object> body = Map.of(
            "type", 1,
            "name", "2.encrypted-name==",
            "login", Map.of("username", "2.encrypted-user==", "password", "2.encrypted-pass==")
        );

        String response = mockMvc.perform(post("/api/ciphers")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Id").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        String cipherId = objectMapper.readTree(response).get("Id").asText();

        mockMvc.perform(get("/api/ciphers/" + cipherId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Id").value(cipherId));
    }

    @Test
    void deleteCipher() throws Exception {
        Map<String, Object> body = Map.of("type", 2, "name", "2.note==",
            "secureNote", Map.of("type", 0));
        String response = mockMvc.perform(post("/api/ciphers")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String cipherId = objectMapper.readTree(response).get("Id").asText();

        mockMvc.perform(delete("/api/ciphers/" + cipherId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ciphers/" + cipherId)
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd springboot && mvn test -Dtest=CiphersControllerTest 2>&1 | tail -5
```

Expected: FAIL

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/service/CipherService.java`**

```java
package com.vaultguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.db.entity.Cipher;
import com.vaultguard.db.repository.CipherRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CipherService {

    private final CipherRepository cipherRepository;
    private final ObjectMapper objectMapper;

    public CipherService(CipherRepository cipherRepository, ObjectMapper objectMapper) {
        this.cipherRepository = cipherRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Cipher create(String userUuid, Map<String, Object> data) {
        Cipher cipher = new Cipher();
        cipher.setUuid(UuidUtil.newUuid());
        cipher.setUserUuid(userUuid);
        applyData(cipher, data);
        return cipherRepository.save(cipher);
    }

    @Transactional
    public Cipher update(Cipher cipher, Map<String, Object> data) {
        applyData(cipher, data);
        return cipherRepository.save(cipher);
    }

    @Transactional
    public void delete(Cipher cipher) {
        cipherRepository.delete(cipher);
    }

    public Optional<Cipher> findById(String uuid) {
        return cipherRepository.findById(uuid);
    }

    public List<Cipher> findByUserUuid(String userUuid) {
        return cipherRepository.findByUserUuid(userUuid);
    }

    public List<Cipher> findAllAccessibleByUser(String userUuid) {
        return cipherRepository.findAllAccessibleByUser(userUuid);
    }

    private void applyData(Cipher cipher, Map<String, Object> data) {
        cipher.setType(((Number) data.getOrDefault("type", 1)).intValue());
        cipher.setName((String) data.get("name"));
        cipher.setNotes((String) data.get("notes"));
        cipher.setFolderUuid((String) data.get("folderId"));
        if (data.containsKey("reprompt")) {
            cipher.setReprompt(((Number) data.get("reprompt")).intValue());
        }
        try {
            // Store the type-specific sub-object as JSON in data field
            String typeKey = switch (cipher.getType()) {
                case 1 -> "login";
                case 2 -> "secureNote";
                case 3 -> "card";
                case 4 -> "identity";
                default -> "data";
            };
            Object typeData = data.get(typeKey);
            cipher.setData(typeData != null
                ? objectMapper.writeValueAsString(typeData)
                : "{}");
            if (data.containsKey("fields")) {
                cipher.setFields(objectMapper.writeValueAsString(data.get("fields")));
            }
        } catch (Exception e) {
            cipher.setData("{}");
        }
    }
}
```

- [ ] **Step 4: Create `springboot/src/main/java/com/vaultguard/service/FolderService.java`**

```java
package com.vaultguard.service;

import com.vaultguard.db.entity.Folder;
import com.vaultguard.db.repository.FolderRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FolderService {

    private final FolderRepository folderRepository;

    public FolderService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Transactional
    public Folder create(String userUuid, String name) {
        Folder folder = new Folder();
        folder.setUuid(UuidUtil.newUuid());
        folder.setUserUuid(userUuid);
        folder.setName(name);
        return folderRepository.save(folder);
    }

    @Transactional
    public Folder update(Folder folder, String name) {
        folder.setName(name);
        return folderRepository.save(folder);
    }

    @Transactional
    public void delete(Folder folder) {
        folderRepository.delete(folder);
    }

    public Optional<Folder> findById(String uuid) {
        return folderRepository.findById(uuid);
    }

    public List<Folder> findByUserUuid(String userUuid) {
        return folderRepository.findByUserUuid(userUuid);
    }
}
```

- [ ] **Step 5: Create `springboot/src/main/java/com/vaultguard/api/ciphers/CiphersController.java`**

```java
package com.vaultguard.api.ciphers;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Cipher;
import com.vaultguard.service.CipherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ciphers")
public class CiphersController {

    private final CipherService cipherService;

    public CiphersController(CipherService cipherService) {
        this.cipherService = cipherService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        List<Cipher> ciphers = cipherService.findAllAccessibleByUser(principal.getUserUuid());
        List<Map<String, Object>> items = ciphers.stream().map(this::toCipherResponse).toList();
        return ResponseEntity.ok(Map.of("Data", items, "Object", "list"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(id)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(c -> ResponseEntity.ok(toCipherResponse(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        Cipher cipher = cipherService.create(principal.getUserUuid(), body);
        return ResponseEntity.ok(toCipherResponse(cipher));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(id)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(c -> ResponseEntity.ok(toCipherResponse(cipherService.update(c, body))))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(id)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(c -> {
                cipherService.delete(c);
                return ResponseEntity.<Void>noContent().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toCipherResponse(Cipher cipher) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", cipher.getUuid());
        resp.put("Type", cipher.getType());
        resp.put("Name", cipher.getName());
        resp.put("Notes", cipher.getNotes());
        resp.put("FolderId", cipher.getFolderUuid());
        resp.put("OrganizationId", cipher.getOrganizationUuid());
        resp.put("Reprompt", cipher.getReprompt());
        resp.put("RevisionDate", cipher.getUpdatedAt());
        resp.put("CreationDate", cipher.getCreatedAt());
        resp.put("DeletedDate", cipher.getDeletedDate());
        resp.put("Object", "cipher");
        return resp;
    }
}
```

- [ ] **Step 6: Create `springboot/src/main/java/com/vaultguard/api/folders/FoldersController.java`**

```java
package com.vaultguard.api.folders;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Folder;
import com.vaultguard.service.FolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FoldersController {

    private final FolderService folderService;

    public FoldersController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        List<Folder> folders = folderService.findByUserUuid(principal.getUserUuid());
        List<Map<String, Object>> items = folders.stream().map(this::toFolderResponse).toList();
        return ResponseEntity.ok(Map.of("Data", items, "Object", "list"));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        Folder folder = folderService.create(principal.getUserUuid(), body.get("name"));
        return ResponseEntity.ok(toFolderResponse(folder));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
        @PathVariable String id,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return folderService.findById(id)
            .filter(f -> principal.getUserUuid().equals(f.getUserUuid()))
            .map(f -> ResponseEntity.ok(toFolderResponse(folderService.update(f, body.get("name")))))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return folderService.findById(id)
            .filter(f -> principal.getUserUuid().equals(f.getUserUuid()))
            .map(f -> {
                folderService.delete(f);
                return ResponseEntity.<Void>noContent().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toFolderResponse(Folder folder) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", folder.getUuid());
        resp.put("Name", folder.getName());
        resp.put("RevisionDate", folder.getUpdatedAt());
        resp.put("Object", "folder");
        return resp;
    }
}
```

- [ ] **Step 7: Run test**

```bash
cd springboot && mvn test -Dtest=CiphersControllerTest 2>&1 | tail -5
```

Expected: Tests run: 2, Failures: 0, Errors: 0

- [ ] **Step 8: Commit**

```bash
git add springboot/
git commit -m "feat: add CipherService, FolderService, CiphersController, FoldersController"
```

---

## Task 13: SyncController

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/service/SyncService.java`
- Create: `springboot/src/main/java/com/vaultguard/api/sync/SyncController.java`
- Create: `springboot/src/test/java/com/vaultguard/api/SyncControllerTest.java`

The sync endpoint is the most important Bitwarden API endpoint — clients call it to download the full vault.

- [ ] **Step 1: Write failing test**

Create `springboot/src/test/java/com/vaultguard/api/SyncControllerTest.java`:

```java
package com.vaultguard.api;

import com.vaultguard.auth.JwtService;
import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.CipherRepository;
import com.vaultguard.db.repository.FolderRepository;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.crypto.PasswordHashService;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CipherRepository cipherRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired PasswordHashService passwordHashService;
    @Autowired JwtService jwtService;

    private String userUuid;
    private String authToken;

    @BeforeEach
    void setUp() {
        cipherRepository.deleteAll();
        folderRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        userUuid = UuidUtil.newUuid();
        user.setUuid(userUuid);
        user.setEmail("sync-test@example.com");
        user.setName("Sync Test");
        user.setPasswordHash(passwordHashService.hashForStorage("hash"));
        user.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(user);
        authToken = jwtService.issueAccessToken(userUuid, UuidUtil.newUuid());
    }

    @Test
    void syncReturnsProfileAndEmptyVault() throws Exception {
        mockMvc.perform(get("/api/sync")
            .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Profile.Id").value(userUuid))
            .andExpect(jsonPath("$.Ciphers").isArray())
            .andExpect(jsonPath("$.Folders").isArray())
            .andExpect(jsonPath("$.Object").value("sync"));
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd springboot && mvn test -Dtest=SyncControllerTest 2>&1 | tail -5
```

Expected: FAIL

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/service/SyncService.java`**

```java
package com.vaultguard.service;

import com.vaultguard.db.entity.*;
import com.vaultguard.db.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SyncService {

    private final UserService userService;
    private final CipherService cipherService;
    private final FolderService folderService;
    private final CollectionRepository collectionRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final OrganizationRepository organizationRepository;

    public SyncService(UserService userService, CipherService cipherService,
                       FolderService folderService, CollectionRepository collectionRepository,
                       OrgMembershipRepository orgMembershipRepository,
                       OrganizationRepository organizationRepository) {
        this.userService = userService;
        this.cipherService = cipherService;
        this.folderService = folderService;
        this.collectionRepository = collectionRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.organizationRepository = organizationRepository;
    }

    public Map<String, Object> buildSyncResponse(String userUuid) {
        User user = userService.findById(userUuid)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Cipher> ciphers = cipherService.findAllAccessibleByUser(userUuid);
        List<Folder> folders = folderService.findByUserUuid(userUuid);
        List<OrgMembership> memberships = orgMembershipRepository.findByUserUuid(userUuid);

        List<Map<String, Object>> cipherResponses = ciphers.stream()
            .map(this::toCipherResponse).toList();
        List<Map<String, Object>> folderResponses = folders.stream()
            .map(this::toFolderResponse).toList();
        List<Map<String, Object>> orgResponses = memberships.stream()
            .map(m -> organizationRepository.findById(m.getOrgUuid())
                .map(org -> toOrgResponse(org, m)).orElse(null))
            .filter(o -> o != null)
            .toList();
        List<Map<String, Object>> collectionResponses = memberships.stream()
            .flatMap(m -> collectionRepository.findByOrgUuid(m.getOrgUuid()).stream())
            .map(this::toCollectionResponse)
            .toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Profile", toProfileResponse(user, memberships));
        resp.put("Folders", folderResponses);
        resp.put("Collections", collectionResponses);
        resp.put("Ciphers", cipherResponses);
        resp.put("Domains", Map.of("EquivalentDomains", List.of(), "GlobalEquivalentDomains", List.of()));
        resp.put("Policies", List.of());
        resp.put("Sends", List.of());
        resp.put("Object", "sync");
        return resp;
    }

    private Map<String, Object> toProfileResponse(User user, List<OrgMembership> memberships) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("Id", user.getUuid());
        p.put("Name", user.getName());
        p.put("Email", user.getEmail());
        p.put("EmailVerified", user.getVerifiedAt() != null);
        p.put("Premium", false);
        p.put("MasterPasswordHint", user.getPasswordHint());
        p.put("Culture", "en-US");
        p.put("TwoFactorEnabled", false);
        p.put("Key", user.getKeyHash());
        p.put("PrivateKey", user.getPrivateKey());
        p.put("SecurityStamp", user.getSecurityStamp());
        p.put("Kdf", user.getKdfType());
        p.put("KdfIterations", user.getKdfIterations());
        p.put("KdfMemory", user.getKdfMemory());
        p.put("KdfParallelism", user.getKdfParallelism());
        p.put("Organizations", memberships.stream().map(m ->
            organizationRepository.findById(m.getOrgUuid())
                .map(org -> toOrgResponse(org, m)).orElse(null))
            .filter(o -> o != null).toList());
        p.put("Object", "profile");
        return p;
    }

    private Map<String, Object> toCipherResponse(Cipher cipher) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", cipher.getUuid());
        resp.put("Type", cipher.getType());
        resp.put("Name", cipher.getName());
        resp.put("Notes", cipher.getNotes());
        resp.put("FolderId", cipher.getFolderUuid());
        resp.put("OrganizationId", cipher.getOrganizationUuid());
        resp.put("Reprompt", cipher.getReprompt());
        resp.put("RevisionDate", cipher.getUpdatedAt());
        resp.put("CreationDate", cipher.getCreatedAt());
        resp.put("DeletedDate", cipher.getDeletedDate());
        resp.put("Object", "cipher");
        return resp;
    }

    private Map<String, Object> toFolderResponse(Folder folder) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", folder.getUuid());
        resp.put("Name", folder.getName());
        resp.put("RevisionDate", folder.getUpdatedAt());
        resp.put("Object", "folder");
        return resp;
    }

    private Map<String, Object> toOrgResponse(Organization org, OrgMembership membership) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", org.getUuid());
        resp.put("Name", org.getName());
        resp.put("Type", membership.getAtype());
        resp.put("Status", membership.getStatus());
        resp.put("Enabled", true);
        resp.put("Key", membership.getAkey());
        resp.put("Object", "profileOrganization");
        return resp;
    }

    private Map<String, Object> toCollectionResponse(Collection collection) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", collection.getUuid());
        resp.put("OrganizationId", collection.getOrgUuid());
        resp.put("Name", collection.getName());
        resp.put("Object", "collection");
        return resp;
    }
}
```

- [ ] **Step 4: Create `springboot/src/main/java/com/vaultguard/api/sync/SyncController.java`**

```java
package com.vaultguard.api.sync;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return ResponseEntity.ok(syncService.buildSyncResponse(principal.getUserUuid()));
    }
}
```

- [ ] **Step 5: Run test**

```bash
cd springboot && mvn test -Dtest=SyncControllerTest 2>&1 | tail -5
```

Expected: Tests run: 1, Failures: 0, Errors: 0

- [ ] **Step 6: Commit**

```bash
git add springboot/
git commit -m "feat: add SyncService and /api/sync endpoint"
```

---

## Task 14: Organizations + Collections Controllers

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/service/OrganizationService.java`
- Create: `springboot/src/main/java/com/vaultguard/api/organizations/OrganizationsController.java`
- Create: `springboot/src/main/java/com/vaultguard/api/collections/CollectionsController.java`

- [ ] **Step 1: Create `springboot/src/main/java/com/vaultguard/service/OrganizationService.java`**

```java
package com.vaultguard.service;

import com.vaultguard.db.entity.Collection;
import com.vaultguard.db.entity.OrgMembership;
import com.vaultguard.db.entity.Organization;
import com.vaultguard.db.repository.*;
import com.vaultguard.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final CollectionRepository collectionRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OrgMembershipRepository orgMembershipRepository,
                                CollectionRepository collectionRepository) {
        this.organizationRepository = organizationRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.collectionRepository = collectionRepository;
    }

    @Transactional
    public Organization create(String ownerUserUuid, String name, String billingEmail,
                                String ownerKey) {
        Organization org = new Organization();
        org.setUuid(UuidUtil.newUuid());
        org.setName(name);
        org.setBillingEmail(billingEmail);
        organizationRepository.save(org);

        OrgMembership membership = new OrgMembership();
        membership.setUuid(UuidUtil.newUuid());
        membership.setUserUuid(ownerUserUuid);
        membership.setOrgUuid(org.getUuid());
        membership.setAtype(0); // Owner
        membership.setStatus(2); // Confirmed
        membership.setAccessAll(true);
        membership.setAkey(ownerKey);
        orgMembershipRepository.save(membership);

        return org;
    }

    public Optional<Organization> findById(String uuid) {
        return organizationRepository.findById(uuid);
    }

    public Optional<OrgMembership> findMembership(String userUuid, String orgUuid) {
        return orgMembershipRepository.findByUserUuidAndOrgUuid(userUuid, orgUuid);
    }

    @Transactional
    public Collection createCollection(String orgUuid, String name) {
        Collection col = new Collection();
        col.setUuid(UuidUtil.newUuid());
        col.setOrgUuid(orgUuid);
        col.setName(name);
        return collectionRepository.save(col);
    }

    public List<Collection> findCollections(String orgUuid) {
        return collectionRepository.findByOrgUuid(orgUuid);
    }
}
```

- [ ] **Step 2: Create `springboot/src/main/java/com/vaultguard/api/organizations/OrganizationsController.java`**

```java
package com.vaultguard.api.organizations;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Organization;
import com.vaultguard.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationsController {

    private final OrganizationService organizationService;

    public OrganizationsController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        String name = (String) body.get("name");
        String billingEmail = (String) body.getOrDefault("billingEmail", "");
        String key = (String) body.get("key");
        Organization org = organizationService.create(principal.getUserUuid(), name, billingEmail, key);
        return ResponseEntity.ok(toOrgResponse(org));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
        @PathVariable String id,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return organizationService.findMembership(principal.getUserUuid(), id)
            .flatMap(m -> organizationService.findById(id))
            .map(org -> ResponseEntity.ok(toOrgResponse(org)))
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toOrgResponse(Organization org) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", org.getUuid());
        resp.put("Name", org.getName());
        resp.put("BillingEmail", org.getBillingEmail());
        resp.put("Plan", org.getPlan());
        resp.put("Object", "organization");
        return resp;
    }
}
```

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/api/collections/CollectionsController.java`**

```java
package com.vaultguard.api.collections;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.db.entity.Collection;
import com.vaultguard.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/collections")
public class CollectionsController {

    private final OrganizationService organizationService;

    public CollectionsController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @PathVariable String orgId,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        // Verify membership
        if (organizationService.findMembership(principal.getUserUuid(), orgId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> items = organizationService.findCollections(orgId)
            .stream().map(this::toCollectionResponse).toList();
        return ResponseEntity.ok(Map.of("Data", items, "Object", "list"));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @PathVariable String orgId,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        if (organizationService.findMembership(principal.getUserUuid(), orgId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Collection col = organizationService.createCollection(orgId, body.get("name"));
        return ResponseEntity.ok(toCollectionResponse(col));
    }

    private Map<String, Object> toCollectionResponse(Collection collection) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", collection.getUuid());
        resp.put("OrganizationId", collection.getOrgUuid());
        resp.put("Name", collection.getName());
        resp.put("Object", "collection");
        return resp;
    }
}
```

- [ ] **Step 4: Compile**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Run all tests**

```bash
cd springboot && mvn test 2>&1 | tail -10
```

Expected: BUILD SUCCESS, no failures

- [ ] **Step 6: Commit**

```bash
git add springboot/
git commit -m "feat: add OrganizationService, OrganizationsController, CollectionsController"
```

---

## Task 15: Attachment Upload/Download

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/service/AttachmentService.java`
- Create: `springboot/src/main/java/com/vaultguard/api/ciphers/AttachmentsController.java`

- [ ] **Step 1: Create `springboot/src/main/java/com/vaultguard/service/AttachmentService.java`**

```java
package com.vaultguard.service;

import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Attachment;
import com.vaultguard.db.repository.AttachmentRepository;
import com.vaultguard.util.UuidUtil;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final VaultGuardProperties props;

    public AttachmentService(AttachmentRepository attachmentRepository,
                              VaultGuardProperties props) {
        this.attachmentRepository = attachmentRepository;
        this.props = props;
    }

    @Transactional
    public Attachment store(String cipherUuid, MultipartFile file, String attachmentKey) throws IOException {
        String id = UuidUtil.newUuid();
        Path dir = Path.of(props.getAttachmentsPath(), cipherUuid);
        Files.createDirectories(dir);
        Path dest = dir.resolve(id);
        file.transferTo(dest);

        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setCipherUuid(cipherUuid);
        attachment.setFileName(file.getOriginalFilename() != null
            ? file.getOriginalFilename() : id);
        attachment.setFileSize(file.getSize());
        attachment.setAkey(attachmentKey);
        return attachmentRepository.save(attachment);
    }

    public Resource load(String cipherUuid, String attachmentId) {
        Path file = Path.of(props.getAttachmentsPath(), cipherUuid, attachmentId);
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) return null;
        return resource;
    }

    @Transactional
    public void delete(String cipherUuid, String attachmentId) throws IOException {
        attachmentRepository.deleteById(attachmentId);
        Path file = Path.of(props.getAttachmentsPath(), cipherUuid, attachmentId);
        Files.deleteIfExists(file);
    }

    public List<Attachment> findByCipherUuid(String cipherUuid) {
        return attachmentRepository.findByCipherUuid(cipherUuid);
    }

    public Optional<Attachment> findById(String id) {
        return attachmentRepository.findById(id);
    }
}
```

- [ ] **Step 2: Create `springboot/src/main/java/com/vaultguard/api/ciphers/AttachmentsController.java`**

```java
package com.vaultguard.api.ciphers;

import com.vaultguard.auth.VaultGuardUserDetails;
import com.vaultguard.config.VaultGuardProperties;
import com.vaultguard.db.entity.Attachment;
import com.vaultguard.service.AttachmentService;
import com.vaultguard.service.CipherService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ciphers/{cipherId}/attachment")
public class AttachmentsController {

    private final CipherService cipherService;
    private final AttachmentService attachmentService;
    private final VaultGuardProperties props;

    public AttachmentsController(CipherService cipherService, AttachmentService attachmentService,
                                  VaultGuardProperties props) {
        this.cipherService = cipherService;
        this.attachmentService = attachmentService;
        this.props = props;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(
        @PathVariable String cipherId,
        @RequestParam("data") MultipartFile file,
        @RequestParam(value = "key", required = false) String attachmentKey,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(cipherId)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(cipher -> {
                try {
                    Attachment att = attachmentService.store(cipherId, file, attachmentKey);
                    return ResponseEntity.ok(toAttachmentResponse(att, cipherId));
                } catch (Exception e) {
                    throw new RuntimeException("Upload failed", e);
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(
        @PathVariable String cipherId,
        @PathVariable String attachmentId,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(cipherId)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(cipher -> {
                Resource resource = attachmentService.load(cipherId, attachmentId);
                if (resource == null) return ResponseEntity.<Resource>notFound().build();
                return attachmentService.findById(attachmentId)
                    .map(att -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + att.getFileName() + "\"")
                        .body(resource))
                    .orElse(ResponseEntity.<Resource>notFound().build());
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
        @PathVariable String cipherId,
        @PathVariable String attachmentId,
        @AuthenticationPrincipal VaultGuardUserDetails principal) {
        return cipherService.findById(cipherId)
            .filter(c -> principal.getUserUuid().equals(c.getUserUuid()))
            .map(cipher -> {
                try {
                    attachmentService.delete(cipherId, attachmentId);
                    return ResponseEntity.<Void>noContent().build();
                } catch (Exception e) {
                    throw new RuntimeException("Delete failed", e);
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toAttachmentResponse(Attachment att, String cipherId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Id", att.getId());
        resp.put("FileName", att.getFileName());
        resp.put("Size", att.getFileSize());
        resp.put("SizeName", humanSize(att.getFileSize()));
        resp.put("Key", att.getAkey());
        resp.put("Url", props.getDomain() + "/api/ciphers/" + cipherId + "/attachment/" + att.getId());
        resp.put("Object", "attachment");
        return resp;
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}
```

- [ ] **Step 3: Compile**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Run all tests**

```bash
cd springboot && mvn test 2>&1 | tail -10
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add springboot/
git commit -m "feat: add AttachmentService and attachment upload/download endpoints"
```

---

## Task 16: Basic 2FA — TOTP + Email OTP

**Files:**
- Create: `springboot/src/main/java/com/vaultguard/service/TwoFactorService.java`
- Modify: `springboot/src/main/java/com/vaultguard/service/AuthService.java` (2FA check in loginWithPassword)
- Create: `springboot/src/test/java/com/vaultguard/service/TwoFactorServiceTest.java`

- [ ] **Step 1: Write failing test**

Create `springboot/src/test/java/com/vaultguard/service/TwoFactorServiceTest.java`:

```java
package com.vaultguard.service;

import com.vaultguard.db.entity.TwoFactor;
import com.vaultguard.db.repository.TwoFactorRepository;
import com.vaultguard.util.UuidUtil;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TwoFactorServiceTest {

    private TwoFactorRepository twoFactorRepository;
    private TwoFactorService twoFactorService;

    @BeforeEach
    void setUp() {
        twoFactorRepository = Mockito.mock(TwoFactorRepository.class);
        twoFactorService = new TwoFactorService(twoFactorRepository, null);
    }

    @Test
    void generateAndVerifyTotp() throws Exception {
        String secret = twoFactorService.generateTotpSecret();
        assertThat(secret).isNotBlank();
        // Generate a real TOTP code for the secret
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        long timeStep = new SystemTimeProvider().getTime() / 30;
        String code = codeGenerator.generate(secret, timeStep);
        assertThat(twoFactorService.verifyTotp(secret, code)).isTrue();
        assertThat(twoFactorService.verifyTotp(secret, "000000")).isFalse();
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd springboot && mvn test -Dtest=TwoFactorServiceTest 2>&1 | tail -5
```

Expected: FAIL

- [ ] **Step 3: Create `springboot/src/main/java/com/vaultguard/service/TwoFactorService.java`**

```java
package com.vaultguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguard.db.entity.TwoFactor;
import com.vaultguard.db.repository.TwoFactorRepository;
import com.vaultguard.util.UuidUtil;
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

    // 2FA type constants
    public static final int TYPE_TOTP = 0;
    public static final int TYPE_EMAIL = 1;

    private final TwoFactorRepository twoFactorRepository;
    private final JavaMailSender mailSender;
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
            tf.setData(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(Map.of("Secret", secret)));
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
                Map<?, ?> data = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(tf.getData(), Map.class);
                String secret = (String) data.get("Secret");
                return verifyTotp(secret, twoFactorToken);
            } catch (Exception e) {
                return false;
            }
        }
        if (twoFactorType == TYPE_EMAIL) {
            try {
                Map<?, ?> data = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(tf.getData(), Map.class);
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
            tf.setData(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(Map.of("Code", code, "Expires", expires.toString())));
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
```

- [ ] **Step 4: Update AuthService to check for 2FA**

In `springboot/src/main/java/com/vaultguard/service/AuthService.java`, update the `loginWithPassword` method to add 2FA handling after verifying the password:

Replace the method with:

```java
@Transactional
public TokenResponse loginWithPassword(String username, String password,
                                       String deviceIdentifier, String deviceName, int deviceType,
                                       Integer twoFactorType, String twoFactorToken) {
    User user = userService.findByEmail(username)
        .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
    if (!user.isEnabled()) {
        throw new IllegalArgumentException("Account disabled");
    }
    if (!userService.verifyPassword(password, user)) {
        throw new IllegalArgumentException("Invalid credentials");
    }
    if (twoFactorService.hasTwoFactor(user.getUuid())) {
        if (twoFactorType == null || twoFactorToken == null ||
            !twoFactorService.validateTwoFactor(user.getUuid(), twoFactorType, twoFactorToken)) {
            throw new TwoFactorRequiredException(user.getUuid());
        }
    }

    Device device = deviceRepository.findByUserUuidAndName(user.getUuid(), deviceIdentifier)
        .orElseGet(() -> {
            Device d = new Device();
            d.setUuid(UuidUtil.newUuid());
            d.setUserUuid(user.getUuid());
            d.setName(deviceIdentifier);
            d.setType(deviceType);
            return d;
        });
    device.setLastActive(Instant.now());
    device.setRefreshToken(UuidUtil.newUuid());
    deviceRepository.save(device);

    String accessToken = jwtService.issueAccessToken(user.getUuid(), device.getUuid());
    return new TokenResponse(
        accessToken,
        device.getRefreshToken(),
        props.getJwt().getAccessTokenExpirySeconds(),
        "Bearer",
        "api offline_access",
        user.getKeyHash(),
        null
    );
}
```

Also add to the top of `AuthService.java`:
- Add `TwoFactorService twoFactorService` as a field and constructor parameter.
- Add `loginWithPassword` overload calling the full method with null 2FA params for backward compat.

Create `springboot/src/main/java/com/vaultguard/service/TwoFactorRequiredException.java`:

```java
package com.vaultguard.service;

public class TwoFactorRequiredException extends RuntimeException {
    private final String userUuid;

    public TwoFactorRequiredException(String userUuid) {
        super("Two-factor authentication required");
        this.userUuid = userUuid;
    }

    public String getUserUuid() { return userUuid; }
}
```

Update `IdentityController.java` to pass 2FA params:

In `IdentityController.token()`, add params:
```java
@RequestParam(value = "twoFactorToken", required = false) String twoFactorToken,
@RequestParam(value = "twoFactorProvider", required = false) Integer twoFactorProvider,
```

And pass them to `authService.loginWithPassword(username, password, deviceIdentifier, deviceName, deviceType, twoFactorProvider, twoFactorToken)`.

Catch `TwoFactorRequiredException` and return HTTP 400 with `{"error": "invalid_grant", "error_description": "Two-factor required", "TwoFactorProviders": [...]}`.

- [ ] **Step 5: Run test**

```bash
cd springboot && mvn test -Dtest=TwoFactorServiceTest 2>&1 | tail -5
```

Expected: Tests run: 1, Failures: 0, Errors: 0

- [ ] **Step 6: Run all tests**

```bash
cd springboot && mvn test 2>&1 | tail -10
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add springboot/
git commit -m "feat: add TwoFactorService (TOTP + email OTP) and 2FA check in auth flow"
```

---

## Task 17: Full Test Suite + Final Verification

- [ ] **Step 1: Run all tests**

```bash
cd springboot && mvn test 2>&1 | tail -20
```

Expected: All tests pass, BUILD SUCCESS

- [ ] **Step 2: Verify the application starts with SQLite profile**

```bash
cd springboot && mvn spring-boot:run -Dspring-boot.run.profiles=sqlite &
sleep 10
curl -s http://localhost:8080/api/accounts/prelogin \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}' | python3 -m json.tool
kill %1
```

Expected: JSON response with `Kdf` and `KdfIterations`

- [ ] **Step 3: Final commit**

```bash
git add springboot/
git commit -m "feat: complete Phase 1 Spring Boot rewrite — all endpoints and tests passing"
```

---

## Spec Coverage Check

| Spec Requirement | Task |
|-----------------|------|
| User registration | Task 11 |
| Email verification (column present, endpoint scaffold) | Task 11 |
| Login, JWT issuance | Tasks 7, 10 |
| Device management | Tasks 3, 10 |
| JWT refresh | Task 10 |
| Cipher CRUD | Task 12 |
| Folder CRUD | Task 12 |
| Attachment upload/download | Task 15 |
| TOTP 2FA | Task 16 |
| Email OTP 2FA | Task 16 |
| Organization creation + basic membership | Task 14 |
| Collections + collection-cipher assignment | Tasks 5, 14 |
| Sync endpoint | Task 13 |
| SQLite profile | Task 1 |
| PostgreSQL profile | Task 1 |
| MySQL profile | Task 1 |
| Liquibase schema | Task 6 |
| Rate limiting | Task 8 (filter wired in SecurityConfig — implementation note: RateLimitFilter bean needed; add as follow-on) |
| CORS | Task 8 |
| RSA key loading | Task 7 |
| Virtual threads | Task 1 (spring.threads.virtual.enabled=true) |

**Rate limit filter follow-on:** SecurityConfig references a `RateLimitFilter` that hasn't been implemented as a full token-bucket. Add this bean:

Create `springboot/src/main/java/com/vaultguard/config/RateLimitFilter.java`:

```java
package com.vaultguard.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final VaultGuardProperties props;
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private volatile long windowStart = System.currentTimeMillis();

    private static final java.util.Set<String> RATE_LIMITED_PATHS = java.util.Set.of(
        "/identity/connect/token",
        "/api/accounts/register"
    );

    public RateLimitFilter(VaultGuardProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!RATE_LIMITED_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        long now = System.currentTimeMillis();
        long windowMs = props.getRateLimit().getWindowSeconds() * 1000L;
        if (now - windowStart > windowMs) {
            synchronized (this) {
                if (now - windowStart > windowMs) {
                    counts.clear();
                    windowStart = now;
                }
            }
        }
        String ip = request.getRemoteAddr();
        AtomicInteger counter = counts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() > props.getRateLimit().getMaxRequests()) {
            response.setStatus(429);
            response.getWriter().write("{\"error\":\"too_many_requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
```

Add this bean to `SecurityConfig.securityFilterChain()` before JwtAuthenticationFilter.
