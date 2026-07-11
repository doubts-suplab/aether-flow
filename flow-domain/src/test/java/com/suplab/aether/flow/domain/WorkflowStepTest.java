package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowStepTest {

    @Test
    void factoriesProduceExpectedTypes() {
        assertThat(WorkflowStep.automated("a", "A", "b").type()).isEqualTo(StepType.AUTOMATED);
        assertThat(WorkflowStep.humanApproval("a", "A", 30, "reviewer", "b").type())
                .isEqualTo(StepType.HUMAN_APPROVAL);
        assertThat(WorkflowStep.end("e", "E").type()).isEqualTo(StepType.END);
    }

    @Test
    void humanApprovalCarriesSlaAndRole() {
        var step = WorkflowStep.humanApproval("review", "Review", 45, "finance-manager", "next");
        assertThat(step.slaMinutes()).isEqualTo(45);
        assertThat(step.assignedRole()).isEqualTo("finance-manager");
        assertThat(step.type().requiresHuman()).isTrue();
    }

    @Test
    void humanApprovalRoleDefaultsWhenBlank() {
        assertThat(WorkflowStep.humanApproval("review", "Review", 10, " ", "next").assignedRole())
                .isEqualTo("reviewer");
    }

    @Test
    void nonApprovalStepsHaveNoRole() {
        assertThat(WorkflowStep.automated("a", "A", "b").assignedRole()).isNull();
        assertThat(WorkflowStep.end("e", "E").assignedRole()).isNull();
    }

    @Test
    void nameDefaultsToKeyWhenBlank() {
        assertThat(WorkflowStep.automated("a", " ", "b").name()).isEqualTo("a");
    }

    @Test
    void stepTypeFlags() {
        assertThat(StepType.HUMAN_APPROVAL.requiresHuman()).isTrue();
        assertThat(StepType.AUTOMATED.requiresHuman()).isFalse();
        assertThat(StepType.AGENT.requiresHuman()).isFalse();
        assertThat(StepType.END.isTerminal()).isTrue();
        assertThat(StepType.AUTOMATED.isTerminal()).isFalse();
    }

    @Test
    void rejectsBlankKeyAndNullTypeAndNegativeSla() {
        assertThatThrownBy(() -> new WorkflowStep(" ", "n", StepType.END, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("step key");
        assertThatThrownBy(() -> new WorkflowStep("k", "n", null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
        assertThatThrownBy(() -> new WorkflowStep("k", "n", StepType.AUTOMATED, -1, null, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("slaMinutes");
    }

    @Test
    void endStepMustNotDeclareNext() {
        assertThatThrownBy(() -> new WorkflowStep("e", "E", StepType.END, 0, null, "next"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not declare");
    }

    @Test
    void nonTerminalStepRequiresNext() {
        assertThatThrownBy(() -> new WorkflowStep("a", "A", StepType.AUTOMATED, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a nextStepKey");
        assertThatThrownBy(() -> new WorkflowStep("a", "A", StepType.AUTOMATED, 0, null, " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a nextStepKey");
    }
}
