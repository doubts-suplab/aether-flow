package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStatus;
import com.suplab.aether.flow.ports.WorkflowEnginePort;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowInstanceControllerTest {

    private static final String TENANT = "acme";
    private static final String WF = "invoice-approval";

    private static WorkflowInstance instance(UUID id, WorkflowStatus status) {
        return new WorkflowInstance(id, TENANT, WF, 1, "INV-1", "review", status,
                Instant.now(), Instant.now(), status.isTerminal() ? Instant.now() : null);
    }

    /** Fake engine whose behaviour per method is set per-test. */
    private static final class FakeEngine implements WorkflowEnginePort {
        RuntimeException startError;
        RuntimeException cancelError;
        WorkflowInstance startResult;
        WorkflowInstance cancelResult;
        UUID lastCancelId;
        String lastCancelledBy;

        @Override public WorkflowInstance start(FlowScope scope, String businessKey) {
            if (startError != null) throw startError;
            return startResult;
        }
        @Override public WorkflowInstance approve(String t, UUID id, String by, String c) { throw new UnsupportedOperationException(); }
        @Override public WorkflowInstance reject(String t, UUID id, String by, String c) { throw new UnsupportedOperationException(); }
        @Override public WorkflowInstance cancel(String t, UUID id, String by, String reason) {
            lastCancelId = id;
            lastCancelledBy = by;
            if (cancelError != null) throw cancelError;
            return cancelResult;
        }
    }

    /** Fake store returning fixed lookups and per-status counts. */
    private static final class FakeStore implements WorkflowInstanceStore {
        WorkflowInstance byId;
        long countPerStatus = 0;

        @Override public void save(WorkflowInstance instance) { }
        @Override public Optional<WorkflowInstance> findById(FlowScope s, UUID id) { return Optional.ofNullable(byId); }
        @Override public Optional<WorkflowInstance> findByTenantAndId(String t, UUID id) { return Optional.ofNullable(byId); }
        @Override public List<WorkflowInstance> findByStatus(FlowScope s, WorkflowStatus st, int limit) {
            return byId != null ? List.of(byId) : List.of();
        }
        @Override public long countByStatus(FlowScope s, WorkflowStatus st) { return countPerStatus; }
    }

    private WorkflowInstanceController controller(FakeEngine e, FakeStore s) {
        return new WorkflowInstanceController(e, s);
    }

    @Test
    void start_returns201() {
        var e = new FakeEngine();
        e.startResult = instance(UUID.randomUUID(), WorkflowStatus.WAITING_APPROVAL);
        var res = controller(e, new FakeStore()).start(TENANT, WF, Map.of("businessKey", "INV-1"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void start_returns409WhenNoActiveDefinition() {
        var e = new FakeEngine();
        e.startError = new IllegalStateException("no active definition");
        var res = controller(e, new FakeStore()).start(TENANT, WF, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void list_rejectsUnknownStatus() {
        var res = controller(new FakeEngine(), new FakeStore()).list(TENANT, WF, "NONSENSE", 20);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void get_returns404WhenMissing() {
        var res = controller(new FakeEngine(), new FakeStore()).get(TENANT, WF, UUID.randomUUID());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancel_returns200AndPassesCancelledBy() {
        var e = new FakeEngine();
        var id = UUID.randomUUID();
        e.cancelResult = instance(id, WorkflowStatus.CANCELLED);
        var res = controller(e, new FakeStore())
                .cancel(TENANT, WF, id, new WorkflowInstanceController.CancelRequest("ops@acme", "duplicate"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(e.lastCancelledBy).isEqualTo("ops@acme");
        assertThat(e.lastCancelId).isEqualTo(id);
    }

    @Test
    void cancel_rejectsMissingCancelledBy() {
        var res = controller(new FakeEngine(), new FakeStore())
                .cancel(TENANT, WF, UUID.randomUUID(), new WorkflowInstanceController.CancelRequest(" ", null));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancel_returns404WhenNotFound() {
        var e = new FakeEngine();
        e.cancelError = new IllegalArgumentException("instance not found");
        var res = controller(e, new FakeStore())
                .cancel(TENANT, WF, UUID.randomUUID(), new WorkflowInstanceController.CancelRequest("ops", null));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancel_returns409WhenTerminal() {
        var e = new FakeEngine();
        e.cancelError = new IllegalStateException("already terminal");
        var res = controller(e, new FakeStore())
                .cancel(TENANT, WF, UUID.randomUUID(), new WorkflowInstanceController.CancelRequest("ops", null));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void stats_returnsCountsPerStatus() {
        var s = new FakeStore();
        s.countPerStatus = 3;
        var res = controller(new FakeEngine(), s).stats(TENANT, WF);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var counts = (Map<String, Long>) ((Map<?, ?>) res.getBody()).get("counts");
        assertThat(counts).containsKeys("RUNNING", "WAITING_APPROVAL", "COMPLETED", "CANCELLED");
        assertThat(counts.get("RUNNING")).isEqualTo(3L);
    }
}
