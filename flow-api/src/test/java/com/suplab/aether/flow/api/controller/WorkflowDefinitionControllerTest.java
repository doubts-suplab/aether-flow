package com.suplab.aether.flow.api.controller;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.ports.WorkflowDefinitionStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDefinitionControllerTest {

    private static final String TENANT = "acme";
    private static final String WF = "invoice-approval";

    /** In-memory definition store mirroring the JDBC semantics (active-by-version, by-version). */
    private static final class FakeStore implements WorkflowDefinitionStore {
        final Map<UUID, WorkflowDefinition> byId = new ConcurrentHashMap<>();

        @Override public void save(WorkflowDefinition d) { byId.put(d.id(), d); }
        @Override public Optional<WorkflowDefinition> findActive(FlowScope s) {
            return byId.values().stream()
                    .filter(d -> d.tenantId().equals(s.tenantId()) && d.workflowKey().equals(s.workflowKey()) && d.active())
                    .max(Comparator.comparingInt(WorkflowDefinition::version));
        }
        @Override public Optional<WorkflowDefinition> findByVersion(FlowScope s, int version) {
            return byId.values().stream()
                    .filter(d -> d.tenantId().equals(s.tenantId()) && d.workflowKey().equals(s.workflowKey()) && d.version() == version)
                    .findFirst();
        }
        @Override public List<WorkflowDefinition> findByTenant(String t, int limit) {
            return byId.values().stream().filter(d -> d.tenantId().equals(t)).toList();
        }
        @Override public void delete(FlowScope s) {
            byId.values().removeIf(d -> d.tenantId().equals(s.tenantId()) && d.workflowKey().equals(s.workflowKey()));
        }
    }

    private static WorkflowDefinitionController.CreateWorkflowRequest request(String... variant) {
        // A minimal valid two-step graph; `variant` lets a second call differ trivially.
        var name = variant.length > 0 ? variant[0] : "Invoice Approval";
        return new WorkflowDefinitionController.CreateWorkflowRequest(WF, name, List.of(
                new WorkflowDefinitionController.StepRequest("intake", "Intake", "AUTOMATED", 0, null, "finish"),
                new WorkflowDefinitionController.StepRequest("finish", "Done", "END", 0, null, null)));
    }

    @SuppressWarnings("unchecked")
    private static int version(Object body) {
        return (int) ((Map<String, Object>) body).get("version");
    }

    @Test
    void firstRegistrationIsVersion1() {
        var store = new FakeStore();
        var res = new WorkflowDefinitionController(store).create(TENANT, request());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(version(res.getBody())).isEqualTo(1);
        assertThat(store.findActive(FlowScope.of(TENANT, WF)).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void secondRegistrationPublishesVersion2AndRetiresVersion1() {
        var store = new FakeStore();
        var controller = new WorkflowDefinitionController(store);
        controller.create(TENANT, request("v1"));

        var res = controller.create(TENANT, request("v2"));

        assertThat(version(res.getBody())).isEqualTo(2);
        // exactly one active version, and it is v2
        var active = store.byId.values().stream().filter(WorkflowDefinition::active).toList();
        assertThat(active).hasSize(1);
        assertThat(active.get(0).version()).isEqualTo(2);
        // v1 still resolvable by version (in-flight instances keep it)
        assertThat(store.findByVersion(FlowScope.of(TENANT, WF), 1)).isPresent();
    }

    @Test
    void invalidNewGraphLeavesTheActiveVersionUntouched() {
        var store = new FakeStore();
        var controller = new WorkflowDefinitionController(store);
        controller.create(TENANT, request("v1"));

        // second registration with a graph that has no END step -> 400, v1 stays active
        var bad = new WorkflowDefinitionController.CreateWorkflowRequest(WF, "bad", List.of(
                new WorkflowDefinitionController.StepRequest("a", "A", "AUTOMATED", 0, null, "b"),
                new WorkflowDefinitionController.StepRequest("b", "B", "AUTOMATED", 0, null, "a")));
        var res = controller.create(TENANT, bad);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(store.findActive(FlowScope.of(TENANT, WF)).orElseThrow().version()).isEqualTo(1);
        assertThat(store.byId.values().stream().filter(WorkflowDefinition::active)).hasSize(1);
    }

    @Test
    void rejectsMissingWorkflowKey() {
        var res = new WorkflowDefinitionController(new FakeStore()).create(TENANT,
                new WorkflowDefinitionController.CreateWorkflowRequest(" ", "x", List.of()));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
