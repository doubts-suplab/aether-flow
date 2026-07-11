package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowInstanceTest {

    private static WorkflowDefinition definition() {
        return WorkflowDefinition.create(FlowScope.of("acme", "wf"), "WF", List.of(
                WorkflowStep.automated("intake", "Intake", "review"),
                WorkflowStep.humanApproval("review", "Review", 60, "reviewer", "finish"),
                WorkflowStep.end("finish", "Done")));
    }

    @Test
    void start_positionsAtStartStepRunning() {
        var instance = WorkflowInstance.start(definition(), "INV-1");
        assertThat(instance.currentStepKey()).isEqualTo("intake");
        assertThat(instance.status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(instance.businessKey()).isEqualTo("INV-1");
        assertThat(instance.completedAt()).isNull();
        assertThat(instance.scope()).isEqualTo(FlowScope.of("acme", "wf"));
    }

    @Test
    void moveTo_advancesAndStaysRunning() {
        var def = definition();
        var instance = WorkflowInstance.start(def, null)
                .moveTo(def.stepByKey("review").orElseThrow());
        assertThat(instance.currentStepKey()).isEqualTo("review");
        assertThat(instance.status()).isEqualTo(WorkflowStatus.RUNNING);
    }

    @Test
    void park_setsWaitingApproval() {
        var def = definition();
        var instance = WorkflowInstance.start(def, null)
                .park(def.stepByKey("review").orElseThrow());
        assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING_APPROVAL);
        assertThat(instance.status().isWaiting()).isTrue();
    }

    @Test
    void complete_setsTerminalWithTimestamp() {
        var instance = WorkflowInstance.start(definition(), null).complete();
        assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(instance.status().isTerminal()).isTrue();
        assertThat(instance.completedAt()).isNotNull();
    }

    @Test
    void rejectCancelFail_areTerminal() {
        assertThat(WorkflowInstance.start(definition(), null).reject().status()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(WorkflowInstance.start(definition(), null).cancel().status()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(WorkflowInstance.start(definition(), null).fail().status()).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void transitionsFromTerminalStateAreRejected() {
        var done = WorkflowInstance.start(definition(), null).complete();
        assertThatThrownBy(done::complete).isInstanceOf(IllegalStateException.class).hasMessageContaining("terminal");
        assertThatThrownBy(done::reject).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(done::cancel).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(done::fail).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statusFlags() {
        assertThat(WorkflowStatus.RUNNING.isTerminal()).isFalse();
        assertThat(WorkflowStatus.WAITING_APPROVAL.isWaiting()).isTrue();
        assertThat(WorkflowStatus.RUNNING.isWaiting()).isFalse();
        assertThat(WorkflowStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThatThrownBy(() -> new WorkflowInstance(null, " ", "wf", 1, null, "s", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new WorkflowInstance(null, "t", "wf", 0, null, "s", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("definitionVersion");
        assertThatThrownBy(() -> new WorkflowInstance(null, "t", "wf", 1, null, " ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currentStepKey");
    }
}
