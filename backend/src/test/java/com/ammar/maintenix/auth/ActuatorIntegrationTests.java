package com.ammar.maintenix.auth;

import com.ammar.maintenix.user.User;
import com.ammar.maintenix.user.UserRepository;
import com.ammar.maintenix.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "app.bootstrap-admin.email=",
        "app.bootstrap-admin.password="
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ActuatorIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void productionEnvironment(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_SECRET", () -> "actuator-integration-test-secret-at-least-32-bytes");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebEndpointsSupplier webEndpointsSupplier;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void healthIsPublicAndExposesOnlyOverallStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void healthDoesNotExposeDetailsEvenToAdmin() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", bearerToken(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyHealthAndInfoAreExposed() {
        assertThat(webEndpointsSupplier.getEndpoints())
                .extracting(endpoint -> endpoint.getEndpointId().toString())
                .containsExactlyInAnyOrder("health", "info");
    }

    @Test
    void infoRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void infoRejectsInvalidJwt() throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TENANT", "TECHNICIAN"})
    void infoRejectsNonAdminJwt(UserRole role) throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .header("Authorization", bearerToken(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessInfoWithoutAutomaticInternalDetails() throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .header("Authorization", bearerToken(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string("{}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator", "/actuator/env", "/actuator/beans", "/actuator/configprops",
            "/actuator/heapdump", "/actuator/threaddump", "/actuator/metrics",
            "/actuator/mappings", "/actuator/loggers", "/actuator/shutdown",
            "/actuator/health/db"
    })
    void otherActuatorPathsAreDeniedEvenToAdmins(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(path)
                        .header("Authorization", bearerToken(UserRole.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/work-orders", "/api/properties", "/api/users"})
    void productionApiStillRequiresAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    private String bearerToken(UserRole role) {
        User user = userRepository.saveAndFlush(new User(
                "actuator-test@example.com", "unused-test-hash", "Test", "User", role));
        return "Bearer " + jwtService.createAccessToken(user);
    }
}
