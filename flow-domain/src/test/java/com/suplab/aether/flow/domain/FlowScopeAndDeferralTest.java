package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowScopeAndDeferralTest {

    @Test
    void flowScope_validatesAndBuilds() {
        var scope = FlowScope.of("acme", "invoice-approval");
        assertThat(scope.tenantId()).isEqualTo("acme");
        assertThat(scope.workflowKey()).isEqualTo("invoice-approval");
    }

    @Test
    void flowScope_rejectsBlanks() {
        assertThatThrownBy(() -> FlowScope.of(" ", "wf")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> FlowScope.of("t", " ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workflowKey");
    }

    @Test
    void deferredDecision_belowGate() {
        var decision = new DeferredDecision("corr-1", "acme", "pricing-agent",
                "Approve 40% discount on order 5567", 0.62, "pricing-manager", null);
        assertThat(decision.isBelowGate()).isTrue();
        assertThat(decision.receivedAt()).isNotNull();
        assertThat(DeferredDecision.CONFIDENCE_GATE).isEqualTo(0.8);
    }

    @Test
    void deferredDecision_atGateIsNotBelow() {
        var decision = new DeferredDecision("corr-2", "acme", "agent", "summary", 0.8, "reviewer", null);
        assertThat(decision.isBelowGate()).isFalse();
    }

    @Test
    void deferredDecision_roleDefaults() {
        var decision = new DeferredDecision("corr-3", "acme", "agent", "summary", 0.5, " ", null);
        assertThat(decision.requestedRole()).isEqualTo("reviewer");
    }

    @Test
    void deferredDecision_rejectsInvalidFields() {
        assertThatThrownBy(() -> new DeferredDecision(" ", "t", "a", "s", 0.5, "r", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("correlationId");
        assertThatThrownBy(() -> new DeferredDecision("c", " ", "a", "s", 0.5, "r", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new DeferredDecision("c", "t", " ", "s", 0.5, "r", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("agentId");
        assertThatThrownBy(() -> new DeferredDecision("c", "t", "a", " ", 0.5, "r", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("summary");
        assertThatThrownBy(() -> new DeferredDecision("c", "t", "a", "s", 1.5, "r", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("confidence");
        assertThatThrownBy(() -> new DeferredDecision("c", "t", "a", "s", -0.1, "r", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("confidence");
    }
}
