package com.suplab.aether.flow.engine.erasure;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.DeferredDecision;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStep;
import com.suplab.aether.flow.engine.support.InMemoryStores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultApprovalErasureServiceTest {

    private InMemoryStores.Instances instances;
    private InMemoryStores.Tasks tasks;
    private DefaultApprovalErasureService erasure;

    @BeforeEach
    void setUp() {
        instances = new InMemoryStores.Instances();
        tasks = new InMemoryStores.Tasks();
        erasure = new DefaultApprovalErasureService(instances, tasks);
    }

    /** Parks a deferral instance under correlationId and raises an approval task for it. */
    private WorkflowInstance parkDeferral(String correlationId) {
        var scope = FlowScope.of("acme", DeferredDecision.WORKFLOW_KEY);
        var definition = WorkflowDefinition.create(scope, "Grid Deferral Review", List.of(
                WorkflowStep.humanApproval("review", "Review", 60, "reviewer", "resolved"),
                WorkflowStep.end("resolved", "Done")));
        var instance = WorkflowInstance.start(definition, correlationId).park(definition.startStep());
        instances.save(instance);
        var task = ApprovalTask.raise(instance, definition.startStep(), "reviewer", Instant.now().plusSeconds(3600));
        tasks.save(task);
        return instance;
    }

    @Test
    void erasesTheDeferralInstanceAndItsApprovalTasks() {
        parkDeferral("corr-1");
        assertThat(instances.size()).isEqualTo(1);
        assertThat(tasks.all()).hasSize(1);

        var result = erasure.eraseDeferral("acme", "corr-1");

        assertThat(result.instancesErased()).isEqualTo(1);
        assertThat(result.tasksErased()).isEqualTo(1);
        assertThat(result.isEmpty()).isFalse();
        assertThat(instances.size()).isZero();
        assertThat(tasks.all()).isEmpty();
    }

    @Test
    void erasingAnUnknownCorrelationIsANoOp() {
        var result = erasure.eraseDeferral("acme", "does-not-exist");

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.instancesErased()).isZero();
        assertThat(result.tasksErased()).isZero();
    }

    @Test
    void erasureIsTenantScoped() {
        parkDeferral("corr-1");

        // A different tenant must not erase acme's deferral.
        var result = erasure.eraseDeferral("other-tenant", "corr-1");

        assertThat(result.isEmpty()).isTrue();
        assertThat(instances.size()).isEqualTo(1);
        assertThat(tasks.all()).hasSize(1);
    }

    @Test
    void rejectsBlankArguments() {
        assertThatThrownBy(() -> erasure.eraseDeferral("", "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> erasure.eraseDeferral("acme", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
