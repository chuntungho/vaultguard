package com.vaultguard.db;

import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.UserRepository;
import com.vaultguard.util.UuidUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.liquibase.enabled=true",
    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml",
    "spring.jpa.hibernate.ddl-auto=none"
})
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

    @Test
    void emailIsUnique() {
        User u1 = new User();
        u1.setUuid(UuidUtil.newUuid());
        u1.setEmail("unique@example.com");
        u1.setName("User 1");
        u1.setPasswordHash("h1");
        u1.setSecurityStamp(UuidUtil.newUuid());
        userRepository.save(u1);

        assertThat(userRepository.existsByEmail("unique@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("other@example.com")).isFalse();
    }
}
