package com.suplab.aether.flow.engine.escalation;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.SlaPolicy;
import com.suplab.aether.flow.engine.support.InMemoryStores;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlaEscalationServiceTest {

    private static final class FakePolicyStore implements SlaPolicyStore {
        final Map<String, SlaPolicy> byTenant = new HashMap<>();
        @Override public Optional<SlaPolicy> find(String tenantId) {
            return Optional.ofNullable(byTenant.get(tenantId));
        }
        @Override public void save(SlaPolicy policy) { byTenant.put(policy.tenantId(), policy); }
    }

    private static final class CountingNotifier implements ApprovalNotificationPort {
        int raised = 0;
        int escalated = 0;
        @Override public void notifyRaised(ApprovalTask task) { raised++; }
        @Override public void notifyEscalated(ApprovalTask task) { escalated++; }
    }

    private static ApprovalTask task(String tenant, String role, ApprovalOutcome outcome, int level) {
        var now = Instant.now();
        return new ApprovalTask(UUID.randomUUID(), tenant, UUID.randomUUID(), "wf", "review", role,
                outcome, now.minusSeconds(60), now.minusSeconds(120), null, null, null, level); // past due
    }

    @Test
    void breachedPendingWithChain_reassignsToFirstRoleAndResetsDeadline() {
        var tenant = "tenant-1";
        var tasks = new InMemoryStores.Tasks();
        var breached = task(tenant, "reviewer", ApprovalOutcome.PENDING, 0);
        tasks.save(breached);
        var policies = new FakePolicyStore();
        policies.save(new SlaPolicy(tenant, 30, List.of("lead", "manager")));
        var notifier = new CountingNotifier();
        var service = new SlaEscalationService(tasks, policies, notifier);

        var result = service.sweep();

        assertThat(result.escalatedCount()).isEqualTo(1);
        var stored = tasks.findById(tenant, breached.id()).orElseThrow();
        assertThat(stored.outcome()).isEqualTo(ApprovalOutcome.ESCALATED);
        assertThat(stored.assignedRole()).isEqualTo("lead");
        assertThat(stored.escalationLevel()).isEqualTo(1);
        assertThat(stored.slaDueAt()).isAfter(Instant.now()); // fresh budget for the next authority
        assertThat(notifier.escalated).isEqualTo(1);
    }

    @Test
    void breachedEscalatedAtLevelOne_advancesToSecondChainRole() {
        var tenant = "tenant-1";
        var tasks = new InMemoryStores.Tasks();
        var atLevelOne = task(tenant, "lead", ApprovalOutcome.ESCALATED, 1);
        tasks.save(atLevelOne);
        var policies = new FakePolicyStore();
        policies.save(new SlaPolicy(tenant, 30, List.of("lead", "manager")));
        var service = new SlaEscalationService(tasks, policies, new CountingNotifier());

        service.sweep();

        var stored = tasks.findById(tenant, atLevelOne.id()).orElseThrow();
        assertThat(stored.assignedRole()).isEqualTo("manager");
        assertThat(stored.escalationLevel()).isEqualTo(2);
    }

    @Test
    void exhaustedChain_leavesTaskEscalatedWithoutChange() {
        var tenant = "tenant-1";
        var tasks = new InMemoryStores.Tasks();
        var exhausted = task(tenant, "manager", ApprovalOutcome.ESCALATED, 2); // chain size 2, level 2
        tasks.save(exhausted);
        var policies = new FakePolicyStore();
        policies.save(new SlaPolicy(tenant, 30, List.of("lead", "manager")));
        var notifier = new CountingNotifier();
        var service = new SlaEscalationService(tasks, policies, notifier);

        var result = service.sweep();

        assertThat(result.escalatedCount()).isZero();
        assertThat(notifier.escalated).isZero();
        assertThat(tasks.findById(tenant, exhausted.id()).orElseThrow().assignedRole()).isEqualTo("manager");
    }

    @Test
    void noPolicy_flagsBreachedPendingOnce_thenIdempotent() {
        var tenant = "tenant-no-policy";
        var tasks = new InMemoryStores.Tasks();
        var breached = task(tenant, "reviewer", ApprovalOutcome.PENDING, 0);
        tasks.save(breached);
        var service = new SlaEscalationService(tasks, new FakePolicyStore(), new CountingNotifier());

        var first = service.sweep();
        assertThat(first.escalatedCount()).isEqualTo(1);
        assertThat(tasks.findById(tenant, breached.id()).orElseThrow().outcome())
                .isEqualTo(ApprovalOutcome.ESCALATED);

        var second = service.sweep(); // already ESCALATED, empty chain → nothing to do
        assertThat(second.escalatedCount()).isZero();
    }
}
