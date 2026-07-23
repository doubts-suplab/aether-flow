package com.suplab.aether.flow.engine.orchestration;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStatus;
import com.suplab.aether.flow.domain.WorkflowStep;
import com.suplab.aether.flow.engine.support.InMemoryStores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultWorkflowOrchestrationServiceTest {

    private static final FlowScope SCOPE = FlowScope.of("acme", "invoice-approval");

    private InMemoryStores.Definitions definitions;
    private InMemoryStores.Instances instances;
    private InMemoryStores.Tasks tasks;
    private DefaultWorkflowOrchestrationService engine;

    @BeforeEach
    void setUp() {
        definitions = new InMemoryStores.Definitions();
        instances = new InMemoryStores.Instances();
        tasks = new InMemoryStores.Tasks();
        engine = new DefaultWorkflowOrchestrationService(definitions, instances, tasks);
    }

    private WorkflowDefinition approvalWorkflow() {
        return WorkflowDefinition.create(SCOPE, "Invoice Approval", List.of(
                WorkflowStep.automated("intake", "Intake", "review"),
                WorkflowStep.humanApproval("review", "Manager review", 120, "finance-manager", "finish"),
                WorkflowStep.end("finish", "Done")));
    }

    @Test
    void start_advancesThroughAutomatedStepsAndParksAtApproval() {
        definitions.save(approvalWorkflow());

        var instance = engine.start(SCOPE, "INV-1001");

        assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING_APPROVAL);
        assertThat(instance.currentStepKey()).isEqualTo("review");
        assertThat(tasks.all()).hasSize(1);
        var task = tasks.all().get(0);
        assertThat(task.outcome()).isEqualTo(ApprovalOutcome.PENDING);
        assertThat(task.assignedRole()).isEqualTo("finance-manager");
        assertThat(task.instanceId()).isEqualTo(instance.id());
    }

    @Test
    void approve_advancesPastGateToCompletion() {
        definitions.save(approvalWorkflow());
        var parked = engine.start(SCOPE, "INV-1001");
        var task = tasks.all().get(0);

        var completed = engine.approve("acme", task.id(), "alice", "ok");

        assertThat(completed.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.id()).isEqualTo(parked.id());
        assertThat(tasks.findById("acme", task.id()).orElseThrow().outcome())
                .isEqualTo(ApprovalOutcome.APPROVED);
    }

    @Test
    void reject_routesToReworkBranchInsteadOfStopping() {
        // review rejects into a "fix" rework step that loops back to review — a non-linear branch.
        definitions.save(WorkflowDefinition.create(SCOPE, "Rework", List.of(
                WorkflowStep.automated("intake", "Intake", "review"),
                WorkflowStep.humanApprovalWithRework("review", "Review", 60, "finance", "finish", "fix"),
                WorkflowStep.automated("fix", "Rework", "review"),
                WorkflowStep.end("finish", "Done"))));
        var parked = engine.start(SCOPE, "INV-1001");
        var firstTask = tasks.all().get(0);

        var reworked = engine.reject("acme", firstTask.id(), "bob", "fix the total");

        // Rejected task is closed; the instance is parked again at review after looping through fix.
        assertThat(tasks.findById("acme", firstTask.id()).orElseThrow().outcome())
                .isEqualTo(ApprovalOutcome.REJECTED);
        assertThat(reworked.status()).isEqualTo(WorkflowStatus.WAITING_APPROVAL);
        assertThat(reworked.currentStepKey()).isEqualTo("review");
        assertThat(reworked.id()).isEqualTo(parked.id());
        // a fresh approval task was raised for the re-review
        assertThat(tasks.findOpenByInstance("acme", parked.id())).isPresent();
        assertThat(tasks.all()).hasSize(2);
        // approving the re-review now completes it
        var secondTask = tasks.findOpenByInstance("acme", parked.id()).orElseThrow();
        assertThat(engine.approve("acme", secondTask.id(), "carol", "ok").status())
                .isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void reject_stopsInstanceInRejected() {
        definitions.save(approvalWorkflow());
        engine.start(SCOPE, "INV-1001");
        var task = tasks.all().get(0);

        var rejected = engine.reject("acme", task.id(), "bob", "missing receipt");

        assertThat(rejected.status()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(tasks.findById("acme", task.id()).orElseThrow().outcome())
                .isEqualTo(ApprovalOutcome.REJECTED);
    }

    @Test
    void start_completesImmediatelyWhenNoHumanStep() {
        definitions.save(WorkflowDefinition.create(SCOPE, "Auto", List.of(
                WorkflowStep.automated("a", "A", "b"),
                WorkflowStep.automated("b", "B", "end"),
                WorkflowStep.end("end", "End"))));

        var instance = engine.start(SCOPE, null);

        assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(tasks.all()).isEmpty();
    }

    @Test
    void multipleApprovalGatesParkAtEachInTurn() {
        definitions.save(WorkflowDefinition.create(SCOPE, "Two Gates", List.of(
                WorkflowStep.humanApproval("first", "First", 60, "reviewer", "second"),
                WorkflowStep.humanApproval("second", "Second", 60, "director", "end"),
                WorkflowStep.end("end", "End"))));

        var parked1 = engine.start(SCOPE, null);
        assertThat(parked1.currentStepKey()).isEqualTo("first");

        var firstTask = tasks.findOpenByInstance("acme", parked1.id()).orElseThrow();
        var parked2 = engine.approve("acme", firstTask.id(), "alice", null);
        assertThat(parked2.status()).isEqualTo(WorkflowStatus.WAITING_APPROVAL);
        assertThat(parked2.currentStepKey()).isEqualTo("second");

        var secondTask = tasks.findOpenByInstance("acme", parked2.id()).orElseThrow();
        assertThat(secondTask.assignedRole()).isEqualTo("director");
        var done = engine.approve("acme", secondTask.id(), "carol", null);
        assertThat(done.status()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void cancel_stopsParkedInstanceAndWithdrawsOpenTask() {
        definitions.save(approvalWorkflow());
        var parked = engine.start(SCOPE, "INV-1001");
        var task = tasks.all().get(0);

        var cancelled = engine.cancel("acme", parked.id(), "ops", "duplicate request");

        assertThat(cancelled.status()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(cancelled.completedAt()).isNotNull();
        assertThat(tasks.findById("acme", task.id()).orElseThrow().outcome())
                .isEqualTo(ApprovalOutcome.WITHDRAWN);
        assertThat(tasks.findOpenByInstance("acme", parked.id())).isEmpty();
    }

    @Test
    void cancel_failsForTerminalInstance() {
        definitions.save(WorkflowDefinition.create(SCOPE, "Auto", List.of(
                WorkflowStep.automated("a", "A", "end"),
                WorkflowStep.end("end", "End"))));
        var done = engine.start(SCOPE, null);

        assertThatThrownBy(() -> engine.cancel("acme", done.id(), "ops", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("terminal");
    }

    @Test
    void cancel_failsForUnknownInstance() {
        assertThatThrownBy(() -> engine.cancel("acme", UUID.randomUUID(), "ops", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("instance not found");
    }

    @Test
    void resumeUsesTheInstancePinnedVersionNotTheNewlyActiveOne() {
        // v1: intake -> review (human) -> finish. Start an instance and park it at review.
        definitions.save(approvalWorkflow());
        var parked = engine.start(SCOPE, "INV-1001");
        var task = tasks.all().get(0);

        // A NEW active version is published while the instance is parked — with a different graph
        // that does not even contain the "review" step. If the engine resolved by "active" it would
        // blow up; version-pinning must keep this instance on v1.
        var v2 = approvalWorkflow().supersede("V2", List.of(
                WorkflowStep.automated("a", "A", "end"),
                WorkflowStep.end("end", "End")));
        definitions.save(v2);
        assertThat(definitions.findActive(SCOPE).orElseThrow().version()).isEqualTo(2);

        var completed = engine.approve("acme", task.id(), "alice", "ok");

        // Resolved against v1 (the pinned version): review -> finish -> COMPLETED.
        assertThat(completed.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(completed.definitionVersion()).isEqualTo(1);
        assertThat(completed.id()).isEqualTo(parked.id());
    }

    @Test
    void start_failsWhenNoActiveDefinition() {
        assertThatThrownBy(() -> engine.start(SCOPE, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no active definition");
    }

    @Test
    void approve_failsForUnknownTask() {
        assertThatThrownBy(() -> engine.approve("acme", UUID.randomUUID(), "alice", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("approval task not found");
    }

    @Test
    void persistsEveryTransition() {
        definitions.save(approvalWorkflow());
        engine.start(SCOPE, "INV-1001");
        // one instance persisted (across intake->review transitions, same id)
        assertThat(instances.size()).isEqualTo(1);
        var stored = instances.findByStatus(SCOPE, WorkflowStatus.WAITING_APPROVAL, 10);
        assertThat(stored).hasSize(1);
    }
}
