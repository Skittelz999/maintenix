package com.ammar.maintenix.auth;

import com.ammar.maintenix.property.Property;
import com.ammar.maintenix.property.PropertyMember;
import com.ammar.maintenix.property.PropertyMemberRepository;
import com.ammar.maintenix.property.PropertyRepository;
import com.ammar.maintenix.user.User;
import com.ammar.maintenix.user.UserRepository;
import com.ammar.maintenix.user.UserRole;
import com.ammar.maintenix.workorder.WorkOrder;
import com.ammar.maintenix.workorder.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CurrentUserAuthorizationIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PropertyRepository properties;

    @Autowired
    private PropertyMemberRepository members;

    @Autowired
    private WorkOrderRepository orders;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    private User admin;
    private User tenant;
    private User technician;
    private Property property;
    private WorkOrder order;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        members.deleteAll();
        properties.deleteAll();
        users.deleteAll();
        admin = createUser("admin@example.com", UserRole.ADMIN);
        tenant = createUser("tenant@example.com", UserRole.TENANT);
        technician = createUser("technician@example.com", UserRole.TECHNICIAN);
        property = properties.saveAndFlush(new Property("Property", "Street", "12345", "City"));
        members.saveAndFlush(new PropertyMember(property, tenant));
        order = orders.saveAndFlush(new WorkOrder(property, tenant, "Repair", "Description"));
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void inactiveUserCannotReuseToken(UserRole role) throws Exception {
        User user = switch (role) {
            case ADMIN -> admin;
            case TENANT -> tenant;
            case TECHNICIAN -> technician;
        };
        String token = jwtService.createAccessToken(user);
        user.setActive(false);
        users.saveAndFlush(user);

        for (MockHttpServletRequestBuilder request : protectedRequests()) {
            mockMvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(
                            "Authentication is required or the access token is invalid"));
        }
        assertThat(users.count()).isEqualTo(3);
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void deletedUserTokenCannotAuthenticateAsReplacementWithSameEmail() throws Exception {
        String token = jwtService.createAccessToken(admin);
        users.delete(admin);
        createUser(admin.getEmail(), UserRole.ADMIN);

        for (MockHttpServletRequestBuilder request : protectedRequests()) {
            mockMvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TENANT", "TECHNICIAN"})
    void demotedAdminCannotKeepAdminPrivilegesOrModifyUnrelatedOrder(UserRole role) throws Exception {
        String token = jwtService.createAccessToken(admin);
        admin.setRole(role);
        users.saveAndFlush(admin);

        for (MockHttpServletRequestBuilder request : adminRequests()) {
            mockMvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(patch("/api/work-orders/{id}/status", order.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/work-orders/{id}", order.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/work-orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(users.count()).isEqualTo(3);
    }

    @Test
    void currentIdentityComesFromUserIdEvenIfOldEmailIsReusedByAdmin() throws Exception {
        String token = jwtService.createAccessToken(tenant);
        String oldEmail = tenant.getEmail();
        tenant.setEmail("renamed@example.com");
        users.saveAndFlush(tenant);
        createUser(oldEmail, UserRole.ADMIN);
        Property other = properties.saveAndFlush(new Property("Other", "Street", "12345", "City"));
        WorkOrder otherOrder = orders.saveAndFlush(new WorkOrder(other, admin, "Other", "Description"));

        mockMvc.perform(get("/api/work-orders/{id}", order.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/work-orders/{id}", otherOrder.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "not-a-uuid", "00000000-0000-0000-0000-000000000000", "wrong-type"})
    void signedTokenRequiresValidExistingUserId(String userId) throws Exception {
        Jwt original = jwtDecoder.decode(jwtService.createAccessToken(admin));
        JwtClaimsSet claims = JwtClaimsSet.builder().claims(values -> {
            values.putAll(original.getClaims());
            if (userId.equals("missing")) values.remove("userId");
            else values.put("userId", userId.equals("wrong-type") ? List.of("invalid") : userId);
        }).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private User createUser(String email, UserRole role) {
        return users.saveAndFlush(new User(email, "unused-test-hash", "Test", "User", role));
    }

    private List<MockHttpServletRequestBuilder> adminRequests() {
        return List.of(
                get("/api/users"), get("/api/users/{id}", tenant.getId()),
                post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"new@example.com","password":"test-password",
                         "firstName":"New","lastName":"Admin","role":"ADMIN"}
                        """),
                post("/api/properties").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"New","addressLine":"Street","postalCode":"12345","city":"City"}
                        """),
                get("/api/properties/{id}", property.getId()),
                get("/api/properties/{id}/members", property.getId()),
                post("/api/properties/{id}/members", property.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + tenant.getId() + "\"}"),
                delete("/api/properties/{id}/members/{userId}", property.getId(), tenant.getId()),
                patch("/api/work-orders/{id}/assign", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + technician.getId() + "\"}"),
                get("/actuator/info"));
    }

    private List<MockHttpServletRequestBuilder> protectedRequests() {
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>(adminRequests());
        requests.addAll(List.of(get("/api/properties"), get("/api/work-orders"),
                get("/api/work-orders/{id}", order.getId()),
                patch("/api/work-orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"),
                post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propertyId\":\"" + property.getId()
                                + "\",\"title\":\"Repair\",\"description\":\"Description\"}")));
        return requests;
    }
}
