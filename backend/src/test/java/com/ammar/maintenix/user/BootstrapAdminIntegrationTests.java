package com.ammar.maintenix.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "app.bootstrap-admin.email=",
        "app.bootstrap-admin.password="
})
@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "bootstrap-test-password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void createsAdminInEmptyDatabaseWithNormalizedEmail(CapturedOutput output) {
        runBootstrap("  ADMIN@Example.COM  ", ADMIN_PASSWORD);

        assertThat(userRepository.count()).isEqualTo(1);
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getFirstName()).isEqualTo("System");
        assertThat(admin.getLastName()).isEqualTo("Admin");
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo(ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(output.getAll()).doesNotContain(ADMIN_PASSWORD, admin.getPasswordHash());
    }

    @Test
    void createsAdminWhenAnotherTenantAlreadyExists() {
        User tenant = userRepository.saveAndFlush(new User(
                "tenant@example.com", "existing-tenant-hash", "Test", "Tenant", UserRole.TENANT));
        User before = userRepository.findById(tenant.getId()).orElseThrow();

        runBootstrap(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(userRepository.count()).isEqualTo(2);
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(userRepository.findById(tenant.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void leavesExistingConfiguredAccountUnchangedRegardlessOfRole(UserRole role, CapturedOutput output) {
        User existing = new User(
                ADMIN_EMAIL, passwordEncoder.encode("existing-password"), "Original", "User", role);
        existing.setActive(false);
        userRepository.saveAndFlush(existing);
        User before = userRepository.findById(existing.getId()).orElseThrow();

        runBootstrap("  ADMIN@Example.COM  ", ADMIN_PASSWORD);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findById(existing.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(before);
        assertThat(output.getAll()).doesNotContain(ADMIN_PASSWORD, before.getPasswordHash());
    }

    @Test
    void repeatedBootstrapDoesNotDuplicateAdminOrOverwritePassword() {
        runBootstrap(ADMIN_EMAIL, ADMIN_PASSWORD);
        User before = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        runBootstrap(ADMIN_EMAIL, "different-bootstrap-password");

        assertThat(userRepository.count()).isEqualTo(1);
        User after = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(after).usingRecursiveComparison().isEqualTo(before);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, after.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("different-bootstrap-password", after.getPasswordHash())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t "})
    void doesNotCreateAccountWhenBothCredentialsAreMissing(String missing) {
        runBootstrap(missing, missing);

        assertThat(userRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "'', bootstrap-test-password",
            "'   ', bootstrap-test-password",
            "admin@example.com, ''",
            "admin@example.com, '   '"
    })
    void rejectsIncompleteCredentialsWithoutCreatingAccount(String email, String password) {
        assertThatThrownBy(() -> runBootstrap(email, password))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Both bootstrap admin email and password must be set");

        assertThat(userRepository.count()).isZero();
    }

    private void runBootstrap(String email, String password) {
        new BootstrapAdmin(userRepository, passwordEncoder, email, password)
                .run(new DefaultApplicationArguments(new String[0]));
    }
}
