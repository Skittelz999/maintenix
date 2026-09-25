package com.ammar.maintenix.workorder;

import com.ammar.maintenix.auth.JwtService;
import com.ammar.maintenix.property.Property;
import com.ammar.maintenix.property.PropertyRepository;
import com.ammar.maintenix.user.User;
import com.ammar.maintenix.user.UserRepository;
import com.ammar.maintenix.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WorkOrderConcurrencyIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository users;

    @Autowired
    private PropertyRepository properties;

    @Autowired
    private WorkOrderRepository orders;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentStartAndReassignmentCannotSilentlyOverwriteEachOther() throws Exception {
        User admin = users.saveAndFlush(new User("admin@example.com", "unused", "Test", "Admin", UserRole.ADMIN));
        User first = users.saveAndFlush(new User("first@example.com", "unused", "First", "Tech", UserRole.TECHNICIAN));
        User second = users.saveAndFlush(new User("second@example.com", "unused", "Second", "Tech", UserRole.TECHNICIAN));
        Property property = properties.saveAndFlush(new Property("Property", "Street", "12345", "City"));
        WorkOrder order = new WorkOrder(property, admin, "Repair", "Description");
        order.setAssignedTo(first);
        order.transitionTo(WorkOrderStatus.ASSIGNED);
        orders.saveAndFlush(order);
        String adminToken = jwtService.createAccessToken(admin);
        String technicianToken = jwtService.createAccessToken(first);

        // Hold only the test fixture's row lock. Both real service transactions
        // can read the original state, but neither UPDATE can finish until release.
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> start;
            Future<MvcResult> assign;
            try (Connection lock = dataSource.getConnection()) {
                lock.setAutoCommit(false);
                try (var statement = lock.prepareStatement("select id from work_orders where id = ? for update")) {
                    statement.setObject(1, order.getId());
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                    }
                }
                start = executor.submit(() -> mockMvc.perform(
                            patch("/api/work-orders/{id}/status", order.getId())
                                    .header("Authorization", "Bearer " + technicianToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\":\"IN_PROGRESS\"}"))
                    .andReturn());
                assign = executor.submit(() -> mockMvc.perform(
                            patch("/api/work-orders/{id}/assign", order.getId())
                                    .header("Authorization", "Bearer " + adminToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"technicianId\":\"" + second.getId() + "\"}"))
                    .andReturn());
                try {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    int waiting = 0;
                    while (System.nanoTime() < deadline) {
                        waiting = jdbc.queryForObject("""
                                select count(*) from pg_stat_activity
                                where datname = current_database() and wait_event_type = 'Lock'
                                  and query like 'update work_orders%'
                                """, Integer.class);
                        if (waiting == 2) break;
                        Thread.sleep(20);
                    }
                    assertThat(waiting).as("Both real updates must overlap").isEqualTo(2);
                } finally {
                    lock.rollback();
                }
            }
            MvcResult startResult = start.get(20, TimeUnit.SECONDS);
            MvcResult assignResult = assign.get(20, TimeUnit.SECONDS);

            assertThat(new int[]{startResult.getResponse().getStatus(), assignResult.getResponse().getStatus()})
                    .containsExactlyInAnyOrder(200, 409);
            MvcResult conflict = startResult.getResponse().getStatus() == 409 ? startResult : assignResult;
            assertThat(conflict.getResponse().getContentAsString())
                    .contains("The record was changed by another request. Reload and try again.")
                    .doesNotContain("Hibernate", "Exception", "SQL", "version");

            WorkOrder stored = orders.findAllWithDetails().getFirst();
            if (startResult.getResponse().getStatus() == 200) {
                assertThat(stored.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
                assertThat(stored.getAssignedTo().getId()).isEqualTo(first.getId());
            } else {
                assertThat(stored.getStatus()).isEqualTo(WorkOrderStatus.ASSIGNED);
                assertThat(stored.getAssignedTo().getId()).isEqualTo(second.getId());
            }
        }
    }
}
