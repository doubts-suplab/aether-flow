package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionTest {

    private static List<WorkflowStep> linearSteps() {
        return List.of(
                WorkflowStep.automated("intake", "Intake", "review"),
                WorkflowStep.humanApproval("review", "Human review", 60, "reviewer", "finish"),
                WorkflowStep.end("finish", "Done"));
    }

    @Test
    void create_buildsActiveVersionOneDefinition() {
        var def = WorkflowDefinition.create(FlowScope.of("acme", "invoice-approval"),
                "Invoice Approval", linearSteps());

        assertThat(def.tenantId()).isEqualTo("acme");
        assertThat(def.workflowKey()).isEqualTo("invoice-approval");
        assertThat(def.version()).isEqualTo(1);
        assertThat(def.active()).isTrue();
        assertThat(def.id()).isNotNull();
        assertThat(def.steps()).hasSize(3);
        assertThat(def.scope()).isEqualTo(FlowScope.of("acme", "invoice-approval"));
    }

    @Test
    void startStep_isFirstDeclaredStep() {
        var def = WorkflowDefinition.create(FlowScope.of("acme", "wf"), "WF", linearSteps());
        assertThat(def.startStep().key()).isEqualTo("intake");
    }

    @Test
    void stepByKey_resolvesAndReportsMissing() {
        var def = WorkflowDefinition.create(FlowScope.of("acme", "wf"), "WF", linearSteps());
        assertThat(def.stepByKey("review")).isPresent();
        assertThat(def.stepByKey("nope")).isEmpty();
    }

    @Test
    void nextStep_followsGraphAndStopsAtEnd() {
        var def = WorkflowDefinition.create(FlowScope.of("acme", "wf"), "WF", linearSteps());
        var intake = def.stepByKey("intake").orElseThrow();
        var end = def.stepByKey("finish").orElseThrow();

        assertThat(def.nextStep(intake)).map(WorkflowStep::key).contains("review");
        assertThat(def.nextStep(end)).isEmpty();
    }

    @Test
    void deactivate_flipsActiveFlag() {
        var def = WorkflowDefinition.create(FlowScope.of("acme", "wf"), "WF", linearSteps());
        assertThat(def.deactivate().active()).isFalse();
    }

    @Test
    void nameDefaultsToWorkflowKeyWhenBlank() {
        var def = WorkflowDefinition.create(FlowScope.of("acme", "wf"), "  ", linearSteps());
        assertThat(def.name()).isEqualTo("wf");
    }

    @Test
    void rejectsMissingTenantOrKey() {
        assertThatThrownBy(() -> new WorkflowDefinition(null, " ", "wf", "n", 1, linearSteps(), true, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new WorkflowDefinition(null, "t", " ", "n", 1, linearSteps(), true, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("workflowKey");
    }

    @Test
    void rejectsVersionBelowOneAndEmptySteps() {
        assertThatThrownBy(() -> new WorkflowDefinition(null, "t", "wf", "n", 0, linearSteps(), true, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> new WorkflowDefinition(null, "t", "wf", "n", 1, List.of(), true, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("steps");
    }

    @Test
    void rejectsGraphWithoutExactlyOneEnd() {
        var noEnd = List.of(WorkflowStep.automated("a", "A", "a"));
        assertThatThrownBy(() -> WorkflowDefinition.create(FlowScope.of("t", "wf"), "n", noEnd))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly one END");

        var twoEnds = List.of(WorkflowStep.end("e1", "E1"), WorkflowStep.end("e2", "E2"));
        assertThatThrownBy(() -> WorkflowDefinition.create(FlowScope.of("t", "wf"), "n", twoEnds))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly one END");
    }

    @Test
    void rejectsDuplicateStepKeys() {
        var dup = List.of(
                WorkflowStep.automated("a", "A", "end"),
                WorkflowStep.automated("a", "A2", "end"),
                WorkflowStep.end("end", "End"));
        assertThatThrownBy(() -> WorkflowDefinition.create(FlowScope.of("t", "wf"), "n", dup))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unique");
    }

    @Test
    void rejectsTransitionToUnknownStep() {
        var bad = List.of(
                WorkflowStep.automated("a", "A", "ghost"),
                WorkflowStep.end("end", "End"));
        assertThatThrownBy(() -> WorkflowDefinition.create(FlowScope.of("t", "wf"), "n", bad))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown step");
    }
}
