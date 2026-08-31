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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationAndAuthorizationIntegrationTests {

    private static final String TENANT_PASSWORD = "tenant-password";
    private static final String TECHNICIAN_PASSWORD = "technician-password";
    private static final String ADMIN_PASSWORD = "admin-password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private PropertyMemberRepository propertyMemberRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    private User tenant;
    private User technician;
    private User otherTechnician;
    private User admin;
    private Property tenantProperty;
    private Property otherProperty;

    @BeforeEach
    void setUp() {
        workOrderRepository.deleteAll();
        propertyMemberRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();

        tenant = userRepository.save(new User(
                "tenant@example.com",
                passwordEncoder.encode(TENANT_PASSWORD),
                "Test",
                "Tenant",
                UserRole.TENANT
        ));
        technician = userRepository.save(new User(
                "technician@example.com",
                passwordEncoder.encode(TECHNICIAN_PASSWORD),
                "Test",
                "Technician",
                UserRole.TECHNICIAN
        ));
        otherTechnician = userRepository.save(new User(
                "other-technician@example.com",
                passwordEncoder.encode("other-technician-password"),
                "Other",
                "Technician",
                UserRole.TECHNICIAN
        ));
        admin = userRepository.save(new User(
                "admin@example.com",
                passwordEncoder.encode(ADMIN_PASSWORD),
                "Test",
                "Admin",
                UserRole.ADMIN
        ));

        tenantProperty = propertyRepository.save(new Property(
                "Tenant Property", "Example street 1", "111 11", "Stockholm"));
        otherProperty = propertyRepository.save(new Property(
                "Other Property", "Other street 2", "222 22", "Stockholm"));
        propertyMemberRepository.save(new PropertyMember(tenantProperty, tenant));
    }

    @Test
    void loginWithCorrectCredentialsReturnsJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "  TENANT@EXAMPLE.COM ",
                                "password", TENANT_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String token = accessToken(result);
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(tenant.getEmail());
        assertThat(jwt.getClaimAsString("userId")).isEqualTo(tenant.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(tenant.getEmail());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("TENANT");
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", tenant.getEmail(),
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void workOrdersWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/work-orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void tenantCreatesWorkOrderForOwnProperty() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .header("Authorization", bearer(login(tenant.getEmail(), TENANT_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createWorkOrderJson(tenantProperty, Map.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propertyId")
                        .value(tenantProperty.getId().toString()))
                .andExpect(jsonPath("$.createdByUserId")
                        .value(tenant.getId().toString()));
    }

    @Test
    void tenantCannotCreateWorkOrderForAnotherProperty() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .header("Authorization", bearer(login(tenant.getEmail(), TENANT_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createWorkOrderJson(otherProperty, Map.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void tenantCannotAssignTechnician() throws Exception {
        WorkOrder workOrder = workOrderRepository.save(
                new WorkOrder(tenantProperty, tenant, "Broken sink", "The sink leaks"));

        mockMvc.perform(patch("/api/work-orders/{id}/assign", workOrder.getId())
                        .header("Authorization", bearer(login(tenant.getEmail(), TENANT_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("technicianId", technician.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void adminAssignsTechnician() throws Exception {
        WorkOrder workOrder = workOrderRepository.save(
                new WorkOrder(tenantProperty, tenant, "Broken sink", "The sink leaks"));

        mockMvc.perform(patch("/api/work-orders/{id}/assign", workOrder.getId())
                        .header("Authorization", bearer(login(admin.getEmail(), ADMIN_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("technicianId", technician.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToUserId")
                        .value(technician.getId().toString()));
    }

    @Test
    void creatorComesFromJwtAndCannotBeSpoofedByRequest() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .header("Authorization", bearer(login(tenant.getEmail(), TENANT_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createWorkOrderJson(
                                tenantProperty,
                                Map.of("createdByUserId", admin.getId())
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByUserId")
                        .value(tenant.getId().toString()));

        WorkOrder saved = workOrderRepository.findAll().getFirst();
        assertThat(saved.getCreatedBy().getId()).isEqualTo(tenant.getId());
    }

    @Test
    void adminListContainsAllWorkOrders() throws Exception {
        WorkOrder tenantOrder = saveWorkOrder(
                tenantProperty, technician, "Tenant order");
        WorkOrder otherOrder = saveWorkOrder(
                otherProperty, otherTechnician, "Other order");
        WorkOrder unassignedOrder = saveWorkOrder(
                otherProperty, null, "Unassigned order");

        MvcResult result = mockMvc.perform(get("/api/work-orders")
                        .header("Authorization", bearer(login(
                                admin.getEmail(), ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(responseIds(result)).containsExactlyInAnyOrder(
                tenantOrder.getId().toString(),
                otherOrder.getId().toString(),
                unassignedOrder.getId().toString()
        );
    }

    @Test
    void adminCanReadAnyWorkOrder() throws Exception {
        WorkOrder workOrder = saveWorkOrder(
                otherProperty, otherTechnician, "Other order");

        mockMvc.perform(get("/api/work-orders/{id}", workOrder.getId())
                        .header("Authorization", bearer(login(
                                admin.getEmail(), ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workOrder.getId().toString()));
    }

    @Test
    void tenantListContainsOnlyOrdersForMemberProperties() throws Exception {
        WorkOrder visibleOrder = saveWorkOrder(
                tenantProperty, null, "Visible tenant order");
        saveWorkOrder(otherProperty, technician, "Hidden other order");

        MvcResult result = mockMvc.perform(get("/api/work-orders")
                        .header("Authorization", bearer(login(
                                tenant.getEmail(), TENANT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(responseIds(result))
                .containsExactly(visibleOrder.getId().toString());
    }

    @Test
    void tenantCanReadOrderForMemberProperty() throws Exception {
        WorkOrder workOrder = saveWorkOrder(
                tenantProperty, null, "Visible tenant order");

        mockMvc.perform(get("/api/work-orders/{id}", workOrder.getId())
                        .header("Authorization", bearer(login(
                                tenant.getEmail(), TENANT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workOrder.getId().toString()));
    }

    @Test
    void tenantCannotReadOrderForAnotherPropertyById() throws Exception {
        WorkOrder workOrder = saveWorkOrder(
                otherProperty, null, "Hidden other order");

        mockMvc.perform(get("/api/work-orders/{id}", workOrder.getId())
                        .header("Authorization", bearer(login(
                                tenant.getEmail(), TENANT_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void technicianListContainsOnlyOwnAssignedOrders() throws Exception {
        WorkOrder ownOrder = saveWorkOrder(
                tenantProperty, technician, "Own assigned order");
        saveWorkOrder(otherProperty, otherTechnician, "Other assigned order");
        saveWorkOrder(otherProperty, null, "Unassigned order");

        MvcResult result = mockMvc.perform(get("/api/work-orders")
                        .header("Authorization", bearer(login(
                                technician.getEmail(), TECHNICIAN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(responseIds(result))
                .containsExactly(ownOrder.getId().toString());
    }

    @Test
    void technicianCanReadOwnAssignedOrder() throws Exception {
        WorkOrder workOrder = saveWorkOrder(
                tenantProperty, technician, "Own assigned order");

        mockMvc.perform(get("/api/work-orders/{id}", workOrder.getId())
                        .header("Authorization", bearer(login(
                                technician.getEmail(), TECHNICIAN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workOrder.getId().toString()));
    }

    @Test
    void technicianCannotReadOrderAssignedToAnotherTechnicianById()
            throws Exception {
        WorkOrder workOrder = saveWorkOrder(
                otherProperty, otherTechnician, "Other assigned order");

        mockMvc.perform(get("/api/work-orders/{id}", workOrder.getId())
                        .header("Authorization", bearer(login(
                                technician.getEmail(), TECHNICIAN_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void technicianCannotReadUnassignedOrderById() throws Exception {
        WorkOrder workOrder = saveWorkOrder(
                otherProperty, null, "Unassigned order");

        mockMvc.perform(get("/api/work-orders/{id}", workOrder.getId())
                        .header("Authorization", bearer(login(
                                technician.getEmail(), TECHNICIAN_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void missingWorkOrderReturnsNotFoundBeforeVisibilityCheck() throws Exception {
        mockMvc.perform(get("/api/work-orders/{id}", java.util.UUID.randomUUID())
                        .header("Authorization", bearer(login(
                                tenant.getEmail(), TENANT_PASSWORD))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return accessToken(result);
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private WorkOrder saveWorkOrder(
            Property property,
            User assignedTo,
            String title
    ) {
        WorkOrder workOrder = new WorkOrder(
                property, tenant, title, "Test description");
        workOrder.setAssignedTo(assignedTo);
        return workOrderRepository.save(workOrder);
    }

    private Set<String> responseIds(MvcResult result) throws Exception {
        Set<String> ids = new java.util.HashSet<>();
        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        response.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private String createWorkOrderJson(Property property, Map<String, Object> additions)
            throws Exception {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("propertyId", property.getId());
        request.put("title", "Broken sink");
        request.put("description", "The sink leaks");
        request.put("priority", "HIGH");
        request.putAll(additions);
        return json(request);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
