package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionVersioningTest {

    private static final FlowScope SCOPE = FlowScope.of("acme", "invoice-approval");

    private static WorkflowDefinition v1() {
        return WorkflowDefinition.create(SCOPE, "Invoice Approval", List.of(
                WorkflowStep.automated("intake", "Intake", "finish"),
                WorkflowStep.end("finish", "Done")));
    }

    @Test
    void supersedeBumpsVersionKeepsScopeMintsNewIdentity() {
        var v1 = v1();
        var v2 = v1.supersede("Invoice Approval v2", List.of(
                WorkflowStep.humanApproval("review", "Review", 60, "finance", "finish"),
                WorkflowStep.end("finish", "Done")));

        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.active()).isTrue();
        assertThat(v2.id()).isNotEqualTo(v1.id());
        assertThat(v2.tenantId()).isEqualTo("acme");
        assertThat(v2.workflowKey()).isEqualTo("invoice-approval");
        assertThat(v2.name()).isEqualTo("Invoice Approval v2");
        // a third supersede stacks the version
        assertThat(v2.supersede(null, v1.steps()).version()).isEqualTo(3);
    }

    @Test
    void supersedeFallsBackToPriorNameWhenBlank() {
        var v2 = v1().supersede("  ", List.of(WorkflowStep.end("finish", "Done")));
        assertThat(v2.name()).isEqualTo("Invoice Approval");
    }

    @Test
    void supersedeValidatesTheNewGraph() {
        // no END step -> invalid, throws before any version is minted
        assertThatThrownBy(() -> v1().supersede("bad", List.of(
                WorkflowStep.automated("a", "A", "b"),
                WorkflowStep.automated("b", "B", "a"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("END");
    }
}
