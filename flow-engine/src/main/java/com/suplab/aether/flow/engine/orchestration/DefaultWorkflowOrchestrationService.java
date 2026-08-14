package com.suplab.aether.flow.engine.orchestration;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStep;
import com.suplab.aether.flow.ports.ApprovalMetricsPort;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.WorkflowDefinitionStore;
import com.suplab.aether.flow.ports.WorkflowEnginePort;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * The workflow orchestration state machine — the default {@link WorkflowEnginePort}.
 *
 * <p>Drives an instance forward one step at a time. Automated and agent steps advance immediately;
 * a human-approval step parks the instance in {@code WAITING_APPROVAL} and raises an
 * {@link ApprovalTask}; the end step completes the instance. A human decision resumes a parked
 * instance: an approval advances it past the gate (and onward until the next park or completion), a
 * rejection stops it. Every transition is persisted through the injected stores, so a restarted
 * service resumes instances from their last saved step.</p>
 *
 * <p>The engine is framework-free and depends only on port interfaces — it is assembled by the API
 * module's configuration via constructor injection.</p>
 */
public class DefaultWorkflowOrchestrationService implements WorkflowEnginePort {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowOrchestrationService.class);

    /** Guards against a malformed definition producing an unterminated advance loop. */
    private static final int MAX_TRANSITIONS = 1000;

    private final WorkflowDefinitionStore definitionStore;
    private final WorkflowInstanceStore instanceStore;
    private final ApprovalTaskStore approvalTaskStore;
    private final ApprovalNotificationPort notifier;
    private final ApprovalMetricsPort metrics;

    /** Convenience constructor without a metrics backend — records are no-ops. */
    public DefaultWorkflowOrchestrationService(WorkflowDefinitionStore definitionStore,
                                               WorkflowInstanceStore instanceStore,
                                               ApprovalTaskStore approvalTaskStore,
                                               ApprovalNotificationPort notifier) {
        this(definitionStore, instanceStore, approvalTaskStore, notifier, ApprovalMetricsPort.NO_OP);
    }

    public DefaultWorkflowOrchestrationService(WorkflowDefinitionStore definitionStore,
                                               WorkflowInstanceStore instanceStore,
                                               ApprovalTaskStore approvalTaskStore,
                                               ApprovalNotificationPort notifier,
                                               ApprovalMetricsPort metrics) {
        this.definitionStore = definitionStore;
        this.instanceStore = instanceStore;
        this.approvalTaskStore = approvalTaskStore;
        this.notifier = notifier;
        this.metrics = metrics;
    }

    @Override
    public WorkflowInstance start(FlowScope scope, String businessKey) {
        var definition = definitionStore.findActive(scope)
                .orElseThrow(() -> new IllegalStateException("no active definition for " + scope));
        var instance = WorkflowInstance.start(definition, businessKey);
        instanceStore.save(instance);
        log.info("Started instance id={} tenantId={} workflowKey={} businessKey={}",
                instance.id(), scope.tenantId(), scope.workflowKey(), businessKey);
        return drive(instance, definition);
    }

    @Override
    public WorkflowInstance approve(String tenantId, UUID taskId, String decidedBy, String comment) {
        var task = requireTask(tenantId, taskId);
        approvalTaskStore.save(task.approve(decidedBy, comment));
        metrics.recordApproved(task);
        var instance = requireInstance(tenantId, task.instanceId());
        // Resolve the definition the instance is pinned to — never the (possibly newer) active one —
        // so a version published while this instance was parked cannot change how it resumes.
        var definition = requireDefinition(instance.scope(), instance.definitionVersion());
        var approvalStep = requireStep(definition, instance.currentStepKey());
        var nextStep = definition.nextStep(approvalStep)
                .orElseThrow(() -> new IllegalStateException(
                        "approval step " + approvalStep.key() + " has no successor"));
        var advanced = instance.moveTo(nextStep);
        instanceStore.save(advanced);
        log.info("Approved taskId={} instanceId={} by={} advancing to step={}",
                taskId, instance.id(), decidedBy, nextStep.key());
        return drive(advanced, definition);
    }

    @Override
    public WorkflowInstance reject(String tenantId, UUID taskId, String decidedBy, String comment) {
        var task = requireTask(tenantId, taskId);
        approvalTaskStore.save(task.reject(decidedBy, comment));
        metrics.recordRejected(task);
        var instance = requireInstance(tenantId, task.instanceId());
        var definition = requireDefinition(instance.scope(), instance.definitionVersion());
        var approvalStep = requireStep(definition, instance.currentStepKey());
        // Branch: a reject on a step that declares a rework target routes there (and drives on) rather
        // than terminating the instance — a genuine non-linear path (rework loop).
        if (approvalStep.reworkStepKey() != null) {
            var reworkStep = requireStep(definition, approvalStep.reworkStepKey());
            var reworked = instance.moveTo(reworkStep);
            instanceStore.save(reworked);
            log.info("Rejected taskId={} instanceId={} by={} — routed to rework step={}",
                    taskId, instance.id(), decidedBy, reworkStep.key());
            return drive(reworked, definition);
        }
        var rejected = instance.reject();
        instanceStore.save(rejected);
        log.info("Rejected taskId={} instanceId={} by={} — instance stopped", taskId, instance.id(), decidedBy);
        return rejected;
    }

    @Override
    public WorkflowInstance cancel(String tenantId, UUID instanceId, String cancelledBy, String reason) {
        var instance = requireInstance(tenantId, instanceId);
        if (instance.status().isTerminal())
            throw new IllegalStateException(
                    "instance " + instanceId + " is already in terminal state " + instance.status());
        approvalTaskStore.findOpenByInstance(tenantId, instanceId).ifPresent(task -> {
            approvalTaskStore.save(task.withdraw());
            log.info("Withdrew open taskId={} on cancellation of instanceId={}", task.id(), instanceId);
        });
        var cancelled = instance.cancel();
        instanceStore.save(cancelled);
        log.info("Cancelled instanceId={} tenantId={} by={} reason={}", instanceId, tenantId, cancelledBy, reason);
        return cancelled;
    }

    /**
     * Advances an instance from its current step until it parks at a human-approval gate or reaches
     * a terminal state, persisting every transition.
     */
    private WorkflowInstance drive(WorkflowInstance instance, WorkflowDefinition definition) {
        var current = instance;
        for (int i = 0; i < MAX_TRANSITIONS; i++) {
            var step = requireStep(definition, current.currentStepKey());
            if (step.type().requiresHuman()) {
                var parked = current.park(step);
                instanceStore.save(parked);
                var task = ApprovalTask.raise(parked, step, step.assignedRole());
                approvalTaskStore.save(task);
                notifyRaised(task);
                metrics.recordRaised(task);
                log.info("Parked instanceId={} at approval step={} taskId={} slaDueAt={}",
                        parked.id(), step.key(), task.id(), task.slaDueAt());
                return parked;
            }
            if (step.type().isTerminal()) {
                var completed = current.complete();
                instanceStore.save(completed);
                log.info("Completed instanceId={} at step={}", completed.id(), step.key());
                return completed;
            }
            var next = requireStep(definition, step.nextStepKey());
            current = current.moveTo(next);
            instanceStore.save(current);
        }
        var failed = current.fail();
        instanceStore.save(failed);
        log.warn("Instance id={} exceeded {} transitions — marked FAILED", failed.id(), MAX_TRANSITIONS);
        return failed;
    }

    private ApprovalTask requireTask(String tenantId, UUID taskId) {
        return approvalTaskStore.findById(tenantId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("approval task not found: " + taskId));
    }

    private WorkflowInstance requireInstance(String tenantId, UUID instanceId) {
        return instanceStore.findByTenantAndId(tenantId, instanceId)
                .orElseThrow(() -> new IllegalArgumentException("instance not found: " + instanceId));
    }

    private WorkflowDefinition requireDefinition(FlowScope scope, int version) {
        return definitionStore.findByVersion(scope, version)
                .orElseThrow(() -> new IllegalStateException(
                        "no definition version " + version + " for " + scope));
    }

    private static WorkflowStep requireStep(WorkflowDefinition definition, String stepKey) {
        return definition.stepByKey(stepKey)
                .orElseThrow(() -> new IllegalStateException(
                        "step " + stepKey + " not found in definition " + definition.workflowKey()));
    }

    /** Best-effort raise notification — a failing sink must never break workflow progression. */
    private void notifyRaised(ApprovalTask task) {
        try {
            notifier.notifyRaised(task);
        } catch (RuntimeException e) {
            log.warn("Raise notification failed for taskId={}: {}", task.id(), e.getMessage());
        }
    }
}
