package com.suplab.aether.flow.engine.gateway;

import com.suplab.aether.flow.domain.DeferredDecision;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowStatus;
import com.suplab.aether.flow.engine.support.InMemoryStores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultApprovalGatewayTest {

    private InMemoryStores.Definitions definitions;
    private InMemoryStores.Instances instances;
    private InMemoryStores.Tasks tasks;
    private DefaultApprovalGateway gateway;

    @BeforeEach
    void setUp() {
        definitions = new InMemoryStores.Definitions();
        instances = new InMemoryStores.Instances();
        tasks = new InMemoryStores.Tasks();
        gateway = new DefaultApprovalGateway(definitions, instances, tasks,
                new com.suplab.aether.flow.engine.notification.LoggingApprovalNotifier(), 30);
    }

    private static DeferredDecision decision(String correlationId, String role) {
        return new DeferredDecision(correlationId, "acme", "pricing-agent",
                "Approve 40% discount on order 5567", 0.61, role, null);
    }

    @Test
    void accept_parksInstanceAndRaisesTaskWithDecisionRole() {
        var instance = gateway.accept(decision("corr-1", "pricing-manager"));

        assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING_APPROVAL);
        assertThat(instance.workflowKey()).isEqualTo(DefaultApprovalGateway.DEFERRAL_WORKFLOW_KEY);
        assertThat(instance.businessKey()).isEqualTo("corr-1");

        var task = tasks.findOpenByInstance("acme", instance.id()).orElseThrow();
        assertThat(task.assignedRole()).isEqualTo("pricing-manager");
        assertThat(task.slaDueAt()).isAfter(task.createdAt());
    }

    @Test
    void accept_createsCanonicalDefinitionOnFirstUseThenReuses() {
        gateway.accept(decision("corr-1", "reviewer"));
        var scope = FlowScope.of("acme", DefaultApprovalGateway.DEFERRAL_WORKFLOW_KEY);
        assertThat(definitions.findActive(scope)).isPresent();

        gateway.accept(decision("corr-2", "reviewer"));
        // still exactly one definition for the tenant — reused, not duplicated
        assertThat(definitions.findByTenant("acme", 10)).hasSize(1);
        assertThat(instances.size()).isEqualTo(2);
    }

    @Test
    void accept_differentDeferralsCanRouteToDifferentRoles() {
        var a = gateway.accept(decision("corr-1", "pricing-manager"));
        var b = gateway.accept(decision("corr-2", "legal"));

        assertThat(tasks.findOpenByInstance("acme", a.id()).orElseThrow().assignedRole())
                .isEqualTo("pricing-manager");
        assertThat(tasks.findOpenByInstance("acme", b.id()).orElseThrow().assignedRole())
                .isEqualTo("legal");
    }
}
