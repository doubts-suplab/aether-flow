package com.suplab.aether.flow.engine.gateway;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.DeferredDecision;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStep;
import com.suplab.aether.flow.ports.ApprovalGatewayPort;
import com.suplab.aether.flow.ports.ApprovalMetricsPort;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import com.suplab.aether.flow.ports.WorkflowDefinitionStore;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * The default {@link ApprovalGatewayPort} — Aether Grid's DEFER seam into Aether Flow.
 *
 * <p>When Grid's confidence gate defers a decision, this gateway turns the bounded
 * {@link DeferredDecision} into a running workflow instance parked at a single human-approval gate.
 * It uses a canonical, tenant-scoped {@value #DEFERRAL_WORKFLOW_KEY} definition (a one-approval
 * process), created on first use, so deferrals appear in the same review queue and lifecycle as
 * any other Flow workflow. The reviewing role is taken from the decision itself
 * ({@link DeferredDecision#requestedRole()}), not from the shared definition — different deferrals
 * can route to different roles.</p>
 *
 * <p>The gateway parks the instance directly rather than delegating to the generic engine so the
 * per-decision role is honoured; the resulting instance and task are indistinguishable from
 * engine-produced ones and are resumed through the same {@link WorkflowInstanceStore} and
 * {@link ApprovalTaskStore}.</p>
 */
public class DefaultApprovalGateway implements ApprovalGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultApprovalGateway.class);

    /** Business key of the canonical single-approval workflow used for Grid deferrals. */
    public static final String DEFERRAL_WORKFLOW_KEY = "grid-deferral";
    private static final String REVIEW_STEP = "review";
    private static final String END_STEP = "resolved";

    private final WorkflowDefinitionStore definitionStore;
    private final WorkflowInstanceStore instanceStore;
    private final ApprovalTaskStore approvalTaskStore;
    private final ApprovalNotificationPort notifier;
    private final ApprovalMetricsPort metrics;
    private final SlaPolicyStore policyStore;
    private final int deferralSlaMinutes;

    /** Convenience constructor without a metrics backend — records are no-ops. */
    public DefaultApprovalGateway(WorkflowDefinitionStore definitionStore,
                                  WorkflowInstanceStore instanceStore,
                                  ApprovalTaskStore approvalTaskStore,
                                  ApprovalNotificationPort notifier,
                                  int deferralSlaMinutes) {
        this(definitionStore, instanceStore, approvalTaskStore, notifier, ApprovalMetricsPort.NO_OP,
                null, deferralSlaMinutes);
    }

    /** Convenience constructor without a policy store — deferral deadlines use wall-clock budgets. */
    public DefaultApprovalGateway(WorkflowDefinitionStore definitionStore,
                                  WorkflowInstanceStore instanceStore,
                                  ApprovalTaskStore approvalTaskStore,
                                  ApprovalNotificationPort notifier,
                                  ApprovalMetricsPort metrics,
                                  int deferralSlaMinutes) {
        this(definitionStore, instanceStore, approvalTaskStore, notifier, metrics, null, deferralSlaMinutes);
    }

    /**
     * @param policyStore        optional per-tenant SLA policy store; when present the deferral task's
     *                           deadline honours the tenant's business-hours calendar. {@code null}
     *                           preserves plain wall-clock deadlines.
     * @param deferralSlaMinutes SLA budget for a deferral's approval task before it is escalated
     */
    public DefaultApprovalGateway(WorkflowDefinitionStore definitionStore,
                                  WorkflowInstanceStore instanceStore,
                                  ApprovalTaskStore approvalTaskStore,
                                  ApprovalNotificationPort notifier,
                                  ApprovalMetricsPort metrics,
                                  SlaPolicyStore policyStore,
                                  int deferralSlaMinutes) {
        this.definitionStore = definitionStore;
        this.instanceStore = instanceStore;
        this.approvalTaskStore = approvalTaskStore;
        this.notifier = notifier;
        this.metrics = metrics;
        this.policyStore = policyStore;
        this.deferralSlaMinutes = deferralSlaMinutes;
    }

    @Override
    public WorkflowInstance accept(DeferredDecision decision) {
        var scope = FlowScope.of(decision.tenantId(), DEFERRAL_WORKFLOW_KEY);
        var definition = definitionStore.findActive(scope).orElseGet(() -> createCanonicalDefinition(scope));

        var reviewStep = definition.startStep();
        var instance = WorkflowInstance.start(definition, decision.correlationId()).park(reviewStep);
        instanceStore.save(instance);

        var task = ApprovalTask.raise(instance, reviewStep, decision.requestedRole(),
                raiseDeadline(decision.tenantId(), reviewStep));
        approvalTaskStore.save(task);
        notifyRaised(task);
        metrics.recordRaised(task);

        log.info("Accepted Grid deferral correlationId={} tenantId={} confidence={} -> instanceId={} taskId={} role={}",
                decision.correlationId(), decision.tenantId(), decision.confidence(), instance.id(),
                task.id(), decision.requestedRole());
        return instance;
    }

    private WorkflowDefinition createCanonicalDefinition(FlowScope scope) {
        var steps = List.of(
                WorkflowStep.humanApproval(REVIEW_STEP, "Review deferred decision", deferralSlaMinutes,
                        "reviewer", END_STEP),
                WorkflowStep.end(END_STEP, "Deferral resolved"));
        var definition = WorkflowDefinition.create(scope, "Grid Deferral Review", steps);
        definitionStore.save(definition);
        log.info("Created canonical deferral definition for tenantId={} slaMinutes={}",
                scope.tenantId(), deferralSlaMinutes);
        return definition;
    }

    /**
     * Computes the deferral task's initial SLA deadline, honouring the tenant's business-hours
     * calendar when a policy store is configured. Returns {@code null} — the wall-clock default — when
     * no policy store is wired or the tenant has no stored policy.
     */
    private Instant raiseDeadline(String tenantId, WorkflowStep step) {
        if (policyStore == null) return null;
        return policyStore.find(tenantId)
                .map(policy -> policy.deadlineFrom(Instant.now(), step.slaMinutes()))
                .orElse(null);
    }

    /** Best-effort raise notification — a failing sink must never break deferral intake. */
    private void notifyRaised(ApprovalTask task) {
        try {
            notifier.notifyRaised(task);
        } catch (RuntimeException e) {
            log.warn("Raise notification failed for taskId={}: {}", task.id(), e.getMessage());
        }
    }
}

