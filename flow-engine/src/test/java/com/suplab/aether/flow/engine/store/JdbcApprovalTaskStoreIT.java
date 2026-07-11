package com.suplab.aether.flow.engine.store;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowInstance;
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
class JdbcApprovalTaskStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"))
            .withDatabaseName("aether_flow_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcApprovalTaskStore store;
    private JdbcWorkflowInstanceStore instanceStore;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new JdbcApprovalTaskStore(jdbc);
        instanceStore = new JdbcWorkflowInstanceStore(jdbc);
    }

    /** Persists a parked instance (FK target) and returns the raised task for it. */
    private ApprovalTask raiseTaskFor(String tenantId, String role) {
        var scope = FlowScope.of(tenantId, "wf");
        var def = WorkflowDefinition.create(scope, "WF", List.of(
                WorkflowStep.humanApproval("review", "Review", 60, role, "finish"),
                WorkflowStep.end("finish", "Done")));
        var instance = WorkflowInstance.start(def, "BK-1").park(def.startStep());
        instanceStore.save(instance);
        return ApprovalTask.raise(instance, def.startStep(), role);
    }

    @Test
    void save_andFindById_roundTrip() {
        var tenant = "tenant-" + UUID.randomUUID();
        var task = raiseTaskFor(tenant, "reviewer");
        store.save(task);

        var found = store.findById(tenant, task.id()).orElseThrow();

        assertThat(found.outcome()).isEqualTo(ApprovalOutcome.PENDING);
        assertThat(found.assignedRole()).isEqualTo("reviewer");
        assertThat(found.decidedBy()).isNull();
    }

    @Test
    void save_upsertPersistsApproval() {
        var tenant = "tenant-" + UUID.randomUUID();
        var task = raiseTaskFor(tenant, "reviewer");
        store.save(task);
        store.save(task.approve("alice", "ok"));

        var found = store.findById(tenant, task.id()).orElseThrow();

        assertThat(found.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
        assertThat(found.decidedBy()).isEqualTo("alice");
        assertThat(found.comment()).isEqualTo("ok");
    }

    @Test
    void findOpenByRole_returnsOnlyOpenForRole() {
        var tenant = "tenant-" + UUID.randomUUID();
        var open = raiseTaskFor(tenant, "finance");
        var decided = raiseTaskFor(tenant, "finance");
        store.save(open);
        store.save(decided);
        store.save(decided.approve("bob", null));

        var queue = store.findOpenByRole(tenant, "finance", 10);

        assertThat(queue).extracting(ApprovalTask::id).containsExactly(open.id());
    }

    @Test
    void findOpenByInstance_resolvesParkedInstanceTask() {
        var tenant = "tenant-" + UUID.randomUUID();
        var task = raiseTaskFor(tenant, "reviewer");
        store.save(task);

        assertThat(store.findOpenByInstance(tenant, task.instanceId())).map(ApprovalTask::id)
                .contains(task.id());
    }
}
