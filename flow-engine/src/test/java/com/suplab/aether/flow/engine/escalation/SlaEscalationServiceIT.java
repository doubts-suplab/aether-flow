package com.suplab.aether.flow.engine.escalation;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStep;
import com.suplab.aether.flow.engine.store.JdbcApprovalTaskStore;
import com.suplab.aether.flow.engine.store.JdbcWorkflowInstanceStore;
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
class SlaEscalationServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"))
            .withDatabaseName("aether_flow_test")
            .withUsername("aether")
            .withPassword("aether");

    private SlaEscalationService service;
    private JdbcApprovalTaskStore taskStore;
    private JdbcWorkflowInstanceStore instanceStore;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new NamedParameterJdbcTemplate(dataSource);
        service = new SlaEscalationService(jdbc);
        taskStore = new JdbcApprovalTaskStore(jdbc);
        instanceStore = new JdbcWorkflowInstanceStore(jdbc);
    }

    /** Raises a task with the given SLA budget (0 => already breached at save time). */
    private ApprovalTask raiseTask(String tenantId, int slaMinutes) {
        var scope = FlowScope.of(tenantId, "wf");
        var def = WorkflowDefinition.create(scope, "WF", List.of(
                WorkflowStep.humanApproval("review", "Review", slaMinutes, "reviewer", "finish"),
                WorkflowStep.end("finish", "Done")));
        var instance = WorkflowInstance.start(def, "BK-1").park(def.startStep());
        instanceStore.save(instance);
        var task = ApprovalTask.raise(instance, def.startStep(), "reviewer");
        taskStore.save(task);
        return task;
    }

    @Test
    void sweep_escalatesBreachedPendingTasksOnly() {
        var tenant = "tenant-" + UUID.randomUUID();
        var breached = raiseTask(tenant, 0);       // deadline == now, immediately overdue
        var withinSla = raiseTask(tenant, 600);    // 10h budget, not overdue

        var result = service.sweep();

        assertThat(result.escalatedCount()).isGreaterThanOrEqualTo(1);
        assertThat(taskStore.findById(tenant, breached.id()).orElseThrow().outcome())
                .isEqualTo(ApprovalOutcome.ESCALATED);
        assertThat(taskStore.findById(tenant, withinSla.id()).orElseThrow().outcome())
                .isEqualTo(ApprovalOutcome.PENDING);
    }

    @Test
    void sweep_isIdempotentForAlreadyEscalatedTasks() {
        var tenant = "tenant-" + UUID.randomUUID();
        raiseTask(tenant, 0);

        service.sweep();
        var second = service.sweep();

        // second sweep re-escalates nothing (already ESCALATED, no longer PENDING)
        assertThat(second.escalatedCount()).isZero();
    }
}
