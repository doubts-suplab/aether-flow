package com.suplab.aether.flow.engine.store;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStatus;
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
class JdbcWorkflowInstanceStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"))
            .withDatabaseName("aether_flow_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcWorkflowInstanceStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcWorkflowInstanceStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static WorkflowInstance freshInstance(FlowScope scope) {
        var def = WorkflowDefinition.create(scope, "WF", List.of(
                WorkflowStep.humanApproval("review", "Review", 60, "reviewer", "finish"),
                WorkflowStep.end("finish", "Done")));
        return WorkflowInstance.start(def, "BK-" + UUID.randomUUID());
    }

    @Test
    void save_andFindById_roundTrip() {
        var scope = FlowScope.of("tenant-" + UUID.randomUUID(), "wf");
        var instance = freshInstance(scope);
        store.save(instance);

        var found = store.findById(scope, instance.id()).orElseThrow();

        assertThat(found.currentStepKey()).isEqualTo("review");
        assertThat(found.status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(found.completedAt()).isNull();
    }

    @Test
    void save_upsertPersistsTerminalTransition() {
        var scope = FlowScope.of("tenant-" + UUID.randomUUID(), "wf");
        var instance = freshInstance(scope);
        store.save(instance);
        store.save(instance.complete());

        var found = store.findByTenantAndId(scope.tenantId(), instance.id()).orElseThrow();

        assertThat(found.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(found.completedAt()).isNotNull();
    }

    @Test
    void findByStatus_andCount_scopeToWorkflow() {
        var scope = FlowScope.of("tenant-" + UUID.randomUUID(), "wf");
        store.save(freshInstance(scope));
        store.save(freshInstance(scope));

        assertThat(store.countByStatus(scope, WorkflowStatus.RUNNING)).isEqualTo(2);
        assertThat(store.findByStatus(scope, WorkflowStatus.RUNNING, 10)).hasSize(2);
        assertThat(store.countByStatus(scope, WorkflowStatus.COMPLETED)).isZero();
    }

    @Test
    void findById_isolatesPerWorkflowKey() {
        var scope = FlowScope.of("tenant-" + UUID.randomUUID(), "wf");
        var other = FlowScope.of(scope.tenantId(), "other-wf");
        var instance = freshInstance(scope);
        store.save(instance);

        assertThat(store.findById(other, instance.id())).isEmpty();
    }
}
