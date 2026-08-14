package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.ports.ApprovalMetricsPort;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.WorkflowEnginePort;
import com.suplab.aether.flow.domain.WorkflowInstance;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalTaskControllerTest {

    private static final class FakeTaskStore implements ApprovalTaskStore {
        final Map<UUID, ApprovalTask> byId = new HashMap<>();
        @Override public void save(ApprovalTask task) { byId.put(task.id(), task); }
        @Override public Optional<ApprovalTask> findById(String tenantId, UUID taskId) {
            return Optional.ofNullable(byId.get(taskId)).filter(t -> t.tenantId().equals(tenantId));
        }
        @Override public List<ApprovalTask> findOpenByRole(String tenantId, String role, int limit) {
            return List.of();
        }
        @Override public Optional<ApprovalTask> findOpenByInstance(String tenantId, UUID instanceId) {
            return Optional.empty();
        }
        @Override public List<ApprovalTask> findBreachedOpen(Instant asOf, int limit) { return List.of(); }
        @Override public long countOpen() { return 0; }
    }

    /** Engine is unused by reassign; every method throws to prove it is not called. */
    private static final class UnusedEngine implements WorkflowEnginePort {
        @Override public WorkflowInstance start(FlowScope scope, String businessKey) { throw new AssertionError(); }
        @Override public WorkflowInstance approve(String t, UUID id, String by, String c) { throw new AssertionError(); }
        @Override public WorkflowInstance reject(String t, UUID id, String by, String c) { throw new AssertionError(); }
        @Override public WorkflowInstance cancel(String t, UUID id, String by, String r) { throw new AssertionError(); }
    }

    private static final class RecordingMetrics implements ApprovalMetricsPort {
        int reassigned;
        @Override public void recordRaised(ApprovalTask task) { }
        @Override public void recordApproved(ApprovalTask task) { }
        @Override public void recordRejected(ApprovalTask task) { }
        @Override public void recordReassigned(ApprovalTask task) { reassigned++; }
    }

    private static ApprovalTask openTask(String tenant) {
        var now = Instant.now();
        return new ApprovalTask(UUID.randomUUID(), tenant, UUID.randomUUID(), "wf", "review", "reviewer",
                ApprovalOutcome.PENDING, now.plusSeconds(3600), now, null, null, null, 0);
    }

    @Test
    void reassign_movesTaskToNewRole() {
        var store = new FakeTaskStore();
        var task = openTask("tenant-1");
        store.save(task);
        var metrics = new RecordingMetrics();
        var controller = new ApprovalTaskController(store, new UnusedEngine(), metrics);

        var res = controller.reassign("tenant-1", task.id(),
                new ApprovalTaskController.ReassignRequest("audit"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(store.byId.get(task.id()).assignedRole()).isEqualTo("audit");
        assertThat(metrics.reassigned).isEqualTo(1); // reassignment is metered
    }

    @Test
    void reassign_missingRoleIsBadRequest() {
        var store = new FakeTaskStore();
        var task = openTask("tenant-1");
        store.save(task);
        var res = new ApprovalTaskController(store, new UnusedEngine(), ApprovalMetricsPort.NO_OP)
                .reassign("tenant-1", task.id(), new ApprovalTaskController.ReassignRequest(" "));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reassign_unknownTaskIsNotFound() {
        var res = new ApprovalTaskController(new FakeTaskStore(), new UnusedEngine(), ApprovalMetricsPort.NO_OP)
                .reassign("tenant-1", UUID.randomUUID(), new ApprovalTaskController.ReassignRequest("audit"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reassign_resolvedTaskIsConflict() {
        var store = new FakeTaskStore();
        var decided = openTask("tenant-1").approve("alice", null);
        store.save(decided);
        var res = new ApprovalTaskController(store, new UnusedEngine(), ApprovalMetricsPort.NO_OP)
                .reassign("tenant-1", decided.id(), new ApprovalTaskController.ReassignRequest("audit"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
