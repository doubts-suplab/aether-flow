package com.suplab.aether.flow.engine.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowStep;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcWorkflowDefinitionStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"))
            .withDatabaseName("aether_flow_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcWorkflowDefinitionStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcWorkflowDefinitionStore(new NamedParameterJdbcTemplate(dataSource), new ObjectMapper());
    }

    private static FlowScope uniqueScope() {
        return FlowScope.of("tenant-" + UUID.randomUUID(), "wf-" + UUID.randomUUID());
    }

    private static List<WorkflowStep> steps() {
        return List.of(
                WorkflowStep.humanApproval("review", "Review", 60, "finance-manager", "finish"),
                WorkflowStep.end("finish", "Done"));
    }

    @Test
    void save_andFindActive_roundTripPreservesSteps() {
        var scope = uniqueScope();
        var def = WorkflowDefinition.create(scope, "Invoice", steps());
        store.save(def);

        var found = store.findActive(scope).orElseThrow();

        assertThat(found.name()).isEqualTo("Invoice");
        assertThat(found.steps()).hasSize(2);
        assertThat(found.startStep().type().requiresHuman()).isTrue();
        assertThat(found.startStep().assignedRole()).isEqualTo("finance-manager");
        assertThat(found.startStep().slaMinutes()).isEqualTo(60);
    }

    @Test
    void findActive_excludesDeactivated() {
        var scope = uniqueScope();
        var def = WorkflowDefinition.create(scope, "WF", steps());
        store.save(def);
        store.save(def.deactivate());

        assertThat(store.findActive(scope)).isEmpty();
    }

    @Test
    void findByVersion_resolvesEachVersionIndependentlyOfActive() {
        var scope = uniqueScope();
        var v1 = WorkflowDefinition.create(scope, "WF v1", steps());
        store.save(v1);
        // publish v2 (active) and retire v1 — as the controller does
        store.save(v1.deactivate());
        var v2 = v1.supersede("WF v2", steps());
        store.save(v2);

        assertThat(store.findActive(scope).orElseThrow().version()).isEqualTo(2);
        assertThat(store.findByVersion(scope, 1).orElseThrow().name()).isEqualTo("WF v1");
        assertThat(store.findByVersion(scope, 1).orElseThrow().active()).isFalse();
        assertThat(store.findByVersion(scope, 2).orElseThrow().name()).isEqualTo("WF v2");
        assertThat(store.findByVersion(scope, 99)).isEmpty();
    }

    @Test
    void findByTenant_listsDefinitions() {
        var scope = uniqueScope();
        store.save(WorkflowDefinition.create(scope, "WF", steps()));

        assertThat(store.findByTenant(scope.tenantId(), 10)).hasSize(1);
    }

    @Test
    void delete_removesWithinScope() {
        var scope = uniqueScope();
        store.save(WorkflowDefinition.create(scope, "WF", steps()));

        store.delete(scope);

        assertThat(store.findActive(scope)).isEmpty();
    }
}
