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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
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

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @LocalServerPort
    private int port;

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
    @ValueSource(strings = {"wrong-issuer", "missing-issuer", "missing-expiration", "expired", "future-not-before"})
    void infoRejectsSignedTokenWithInvalidIssuerOrLifetime(String scenario) throws Exception {
        String validToken = bearerToken(UserRole.ADMIN).substring("Bearer ".length());
        JwtClaimsSet claims = JwtClaimsSet.builder().claims(values -> {
            values.putAll(jwtDecoder.decode(validToken).getClaims());
            switch (scenario) {
                case "wrong-issuer" -> values.put("iss", "another-application");
                case "missing-issuer" -> values.remove("iss");
                case "missing-expiration" -> values.remove("exp");
                case "expired" -> {
                    values.put("iat", Instant.now().minusSeconds(3600));
                    values.put("exp", Instant.now().minusSeconds(120));
                }
                case "future-not-before" -> values.put("nbf", Instant.now().plusSeconds(120));
                default -> throw new IllegalArgumentException("Unknown test scenario");
            }
        }).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/actuator/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Authentication is required or the access token is invalid"));
    }

    @Test
    void infoStillRejectsTokenSignedWithAnotherSecret() throws Exception {
        String validToken = bearerToken(UserRole.ADMIN).substring("Bearer ".length());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .claims(values -> values.putAll(jwtDecoder.decode(validToken).getClaims())).build();
        JwtEncoder otherEncoder = new SecurityConfig().jwtEncoder("a-different-test-signing-secret-at-least-32-bytes");
        String token = otherEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/actuator/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nativeTomcatRecognizesHttpsFromTrustedProxy() throws Exception {
        // Use the real server: MockMvc does not execute Tomcat's RemoteIpValve.
        // Loopback is a trusted internal proxy in Tomcat's default configuration.
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                            .timeout(Duration.ofSeconds(10))
                            .header("X-Forwarded-Proto", "https").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
            assertThat(response.headers().firstValue("Strict-Transport-Security")).isPresent();
        }
    }

    @Test
    void internalHttpHealthCheckDoesNotRedirect() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                            .timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Location")).isEmpty();
            assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
        }
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
