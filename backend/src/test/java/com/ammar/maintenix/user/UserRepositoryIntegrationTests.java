package com.ammar.maintenix.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class UserRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void savesAndFindsUserByNormalizedEmail() {
        String passwordHash = "$2a$12$integration-test-hash";
        User user = new User(
                "  Tenant.User@Example.COM  ",
                passwordHash,
                "Tenant",
                "User",
                UserRole.TENANT
        );

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("tenant.user@example.com");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(userRepository.findByEmail("tenant.user@example.com"))
                .get()
                .extracting(User::getId)
                .isEqualTo(saved.getId());
        assertThat(userRepository.existsByEmail("tenant.user@example.com")).isTrue();
        assertThat(saved.toString()).doesNotContain(passwordHash, "passwordHash");
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfInputCase() {
        userRepository.saveAndFlush(new User(
                "technician@example.com",
                "first-hash",
                "First",
                "Technician",
                UserRole.TECHNICIAN
        ));

        User duplicate = new User(
                "TECHNICIAN@EXAMPLE.COM",
                "second-hash",
                "Second",
                "Technician",
                UserRole.TECHNICIAN
        );

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
