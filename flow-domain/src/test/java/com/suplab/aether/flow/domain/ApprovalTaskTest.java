package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalTaskTest {

    private static WorkflowInstance parkedInstance(int slaMinutes) {
        var def = WorkflowDefinition.create(FlowScope.of("acme", "wf"), "WF", List.of(
                WorkflowStep.humanApproval("review", "Review", slaMinutes, "reviewer", "finish"),
                WorkflowStep.end("finish", "Done")));
        return WorkflowInstance.start(def, "INV-9").park(def.startStep());
    }

    private static WorkflowStep reviewStep(int slaMinutes) {
        return WorkflowStep.humanApproval("review", "Review", slaMinutes, "reviewer", "finish");
    }

    @Test
    void raise_buildsPendingTaskWithSlaDeadline() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), "finance-manager");
        assertThat(task.outcome()).isEqualTo(ApprovalOutcome.PENDING);
        assertThat(task.assignedRole()).isEqualTo("finance-manager");
        assertThat(task.stepKey()).isEqualTo("review");
        assertThat(task.slaDueAt()).isAfter(task.createdAt());
        assertThat(task.decidedAt()).isNull();
    }

    @Test
    void approve_recordsDeciderAndComment() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), "reviewer")
                .approve("alice", "looks good");
        assertThat(task.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
        assertThat(task.decidedBy()).isEqualTo("alice");
        assertThat(task.comment()).isEqualTo("looks good");
        assertThat(task.decidedAt()).isNotNull();
    }

    @Test
    void reject_recordsDecider() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), "reviewer")
                .reject("bob", "insufficient evidence");
        assertThat(task.outcome()).isEqualTo(ApprovalOutcome.REJECTED);
        assertThat(task.decidedBy()).isEqualTo("bob");
    }

    @Test
    void escalate_keepsTaskOpen() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), "reviewer").escalate();
        assertThat(task.outcome()).isEqualTo(ApprovalOutcome.ESCALATED);
        assertThat(task.outcome().isOpen()).isTrue();
        // an escalated task can still be decided
        assertThat(task.approve("carol", null).outcome()).isEqualTo(ApprovalOutcome.APPROVED);
    }

    @Test
    void isBreached_trueOnlyWhenOpenAndPastDeadline() {
        var task = ApprovalTask.raise(parkedInstance(0), reviewStep(0), "reviewer");
        // slaMinutes 0 -> deadline == createdAt; a moment later it is breached
        assertThat(task.isBreached(Instant.now().plusSeconds(1))).isTrue();
        assertThat(task.isBreached(task.createdAt().minusSeconds(1))).isFalse();

        var decided = task.approve("alice", null);
        assertThat(decided.isBreached(Instant.now().plusSeconds(10_000))).isFalse();
    }

    @Test
    void withdraw_closesTaskWithoutADecision() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), "reviewer").withdraw();
        assertThat(task.outcome()).isEqualTo(ApprovalOutcome.WITHDRAWN);
        assertThat(task.outcome().isOpen()).isFalse();
        assertThat(task.outcome().isDecided()).isFalse();
        assertThat(task.outcome().isResolved()).isTrue();
        assertThat(task.decidedBy()).isNull();
    }

    @Test
    void withdraw_worksFromEscalated() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), "reviewer").escalate().withdraw();
        assertThat(task.outcome()).isEqualTo(ApprovalOutcome.WITHDRAWN);
    }

    @Test
    void decidingAnAlreadyDecidedTaskFails() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), "reviewer").approve("alice", null);
        assertThatThrownBy(() -> task.approve("bob", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> task.reject("bob", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(task::escalate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(task::withdraw).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveRequiresDecider() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), "reviewer");
        assertThatThrownBy(() -> task.approve(" ", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decidedBy");
    }

    @Test
    void roleDefaultsWhenBlank() {
        var task = ApprovalTask.raise(parkedInstance(60), reviewStep(60), " ");
        assertThat(task.assignedRole()).isEqualTo("reviewer");
    }

    @Test
    void outcomeFlags() {
        assertThat(ApprovalOutcome.PENDING.isOpen()).isTrue();
        assertThat(ApprovalOutcome.ESCALATED.isOpen()).isTrue();
        assertThat(ApprovalOutcome.APPROVED.isOpen()).isFalse();
        assertThat(ApprovalOutcome.APPROVED.isDecided()).isTrue();
        assertThat(ApprovalOutcome.REJECTED.isDecided()).isTrue();
        assertThat(ApprovalOutcome.PENDING.isDecided()).isFalse();
        assertThat(ApprovalOutcome.WITHDRAWN.isOpen()).isFalse();
        assertThat(ApprovalOutcome.WITHDRAWN.isDecided()).isFalse();
        assertThat(ApprovalOutcome.WITHDRAWN.isResolved()).isTrue();
        assertThat(ApprovalOutcome.PENDING.isResolved()).isFalse();
    }

    @Test
    void rejectsInvalidConstruction() {
        var iid = UUID.randomUUID();
        assertThatThrownBy(() -> new ApprovalTask(null, " ", iid, "wf", "s", "r", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new ApprovalTask(null, "t", null, "wf", "s", "r", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("instanceId");
        assertThatThrownBy(() -> new ApprovalTask(null, "t", iid, "wf", " ", "r", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stepKey");
    }
}
