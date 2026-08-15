package com.suplab.aether.flow.engine.escalation;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.SlaPolicy;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.SlaEscalationPort;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Policy-driven implementation of {@link SlaEscalationPort}.
 *
 * <p>Each sweep loads a batch of breached open tasks (oldest deadline first) and routes each one up
 * its tenant's escalation chain: a task at level <em>L</em> whose tenant policy defines a role at
 * <em>L</em> is reassigned to that role, flagged {@code ESCALATED}, given a fresh SLA budget, and its
 * level bumped — so if the next authority also misses the deadline the following sweep escalates
 * further. When the chain is exhausted the task stays {@code ESCALATED} at the top of the queue
 * (visibility only). A tenant with no chain keeps the original behaviour: a breached {@code PENDING}
 * task is flagged {@code ESCALATED} once, without reassignment.</p>
 *
 * <p>Escalation raises visibility and re-routes — it never approves, rejects, or deletes; a human
 * always decides. Every escalation fires an {@link ApprovalNotificationPort#notifyEscalated} signal.
 * Policies are cached per tenant within a single sweep to avoid repeated lookups.</p>
 */
public class SlaEscalationService implements SlaEscalationPort {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

    /** Default cap on tasks processed per sweep. */
    public static final int DEFAULT_BATCH_LIMIT = 500;

    private final ApprovalTaskStore taskStore;
    private final SlaPolicyStore policyStore;
    private final ApprovalNotificationPort notifier;
    private final int batchLimit;

    public SlaEscalationService(ApprovalTaskStore taskStore, SlaPolicyStore policyStore,
                                ApprovalNotificationPort notifier) {
        this(taskStore, policyStore, notifier, DEFAULT_BATCH_LIMIT);
    }

    public SlaEscalationService(ApprovalTaskStore taskStore, SlaPolicyStore policyStore,
                                ApprovalNotificationPort notifier, int batchLimit) {
        this.taskStore = taskStore;
        this.policyStore = policyStore;
        this.notifier = notifier;
        this.batchLimit = batchLimit < 1 ? DEFAULT_BATCH_LIMIT : batchLimit;
    }

    @Override
    public EscalationResult sweep() {
        var now = Instant.now();
        List<ApprovalTask> breached = taskStore.findBreachedOpen(now, batchLimit);
        Map<String, SlaPolicy> policyCache = new HashMap<>();
        long escalated = 0;

        for (ApprovalTask task : breached) {
            var policy = policyCache.computeIfAbsent(task.tenantId(),
                    t -> policyStore.find(t).orElseGet(() -> SlaPolicy.defaultFor(t)));
            var escalatedTask = escalate(task, policy, now);
            if (escalatedTask != null) {
                taskStore.save(escalatedTask);
                safeNotify(escalatedTask);
                escalated++;
            }
        }

        long totalOpen = taskStore.countOpen();
        log.info("SLA escalation sweep complete: scanned={} escalated={} totalOpen={}",
                breached.size(), escalated, totalOpen);
        return new EscalationResult(breached.size(), escalated, totalOpen);
    }

    /**
     * Escalates one breached task, or returns {@code null} when there is nothing left to do (an
     * already-{@code ESCALATED} task whose chain is exhausted).
     */
    private static ApprovalTask escalate(ApprovalTask task, SlaPolicy policy, Instant now) {
        int level = task.escalationLevel();
        if (policy.hasNextLevel(level)) {
            var nextRole = policy.roleAtLevel(level).orElseThrow();
            // Fresh budget for the next authority — measured in working time when the tenant has a
            // business-hours calendar, plain wall-clock otherwise.
            var newDueAt = policy.deadlineFrom(now, policy.defaultSlaMinutes());
            return task.escalate(nextRole, newDueAt);
        }
        if (task.outcome() == ApprovalOutcome.PENDING) {
            // First breach with no (further) chain — flag ESCALATED once, keep role and deadline.
            return task.escalate();
        }
        return null; // already ESCALATED and chain exhausted — stays visible, nothing to change
    }

    private void safeNotify(ApprovalTask task) {
        try {
            notifier.notifyEscalated(task);
        } catch (RuntimeException e) {
            log.warn("Escalation notification failed for taskId={}: {}", task.id(), e.getMessage());
        }
    }
}
