package com.suplab.aether.flow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A human review gate raised when a {@link WorkflowInstance} reaches a
 * {@link StepType#HUMAN_APPROVAL} step.
 *
 * <p>The task is assigned to a role (not an individual, so any holder of the role may action it),
 * carries an SLA deadline ({@code slaDueAt}) derived from the step's {@code slaMinutes}, and holds
 * the human decision once made. An open task past its {@code slaDueAt} is a breach and becomes
 * eligible for {@link #escalate() escalation}. Deciding a task ({@link #approve(String, String)},
 * {@link #reject(String, String)}) records who decided, when, and an optional comment.</p>
 *
 * <p>All fields are immutable; transitions return new instances.</p>
 *
 * @param id         stable identifier
 * @param tenantId   owning tenant (isolation boundary)
 * @param instanceId the workflow instance this gate belongs to
 * @param workflowKey business key of the owning workflow (for scoped queries)
 * @param stepKey    the approval step within the definition
 * @param assignedRole the role expected to action the task
 * @param outcome    current approval state
 * @param slaDueAt   deadline by which a decision is expected
 * @param createdAt  when the task was raised
 * @param decidedAt  when a human decided ({@code null} while open)
 * @param decidedBy  who decided ({@code null} while open)
 * @param comment    optional decision note ({@code null} if none)
 */
public record ApprovalTask(
        UUID id,
        String tenantId,
        UUID instanceId,
        String workflowKey,
        String stepKey,
        String assignedRole,
        ApprovalOutcome outcome,
        Instant slaDueAt,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy,
        String comment
) {
    public ApprovalTask {
        if (id == null) id = UUID.randomUUID();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (instanceId == null) throw new IllegalArgumentException("instanceId required");
        if (workflowKey == null || workflowKey.isBlank())
            throw new IllegalArgumentException("workflowKey required");
        if (stepKey == null || stepKey.isBlank()) throw new IllegalArgumentException("stepKey required");
        if (assignedRole == null || assignedRole.isBlank()) assignedRole = "reviewer";
        if (outcome == null) outcome = ApprovalOutcome.PENDING;
        if (createdAt == null) createdAt = Instant.now();
        if (slaDueAt == null) slaDueAt = createdAt;
    }

    /**
     * Factory raising a fresh {@code PENDING} task for an instance at a human-approval step. The
     * SLA deadline is {@code slaMinutes} after now.
     *
     * @param instance    the parked workflow instance
     * @param step        the human-approval step (supplies {@code slaMinutes})
     * @param assignedRole the role expected to action the task
     */
    public static ApprovalTask raise(WorkflowInstance instance, WorkflowStep step, String assignedRole) {
        var now = Instant.now();
        return new ApprovalTask(UUID.randomUUID(), instance.tenantId(), instance.id(),
                instance.workflowKey(), step.key(), assignedRole, ApprovalOutcome.PENDING,
                now.plusSeconds(60L * Math.max(0, step.slaMinutes())), now, null, null, null);
    }

    /**
     * @return {@code true} if this task is open and its SLA deadline has passed at {@code asOf}.
     */
    public boolean isBreached(Instant asOf) {
        return outcome.isOpen() && asOf != null && asOf.isAfter(slaDueAt);
    }

    /**
     * Returns an approved copy recording the decider and an optional comment.
     */
    public ApprovalTask approve(String decidedBy, String comment) {
        requireOpen();
        return new ApprovalTask(id, tenantId, instanceId, workflowKey, stepKey, assignedRole,
                ApprovalOutcome.APPROVED, slaDueAt, createdAt, Instant.now(), requireDecider(decidedBy), comment);
    }

    /**
     * Returns a rejected copy recording the decider and an optional comment.
     */
    public ApprovalTask reject(String decidedBy, String comment) {
        requireOpen();
        return new ApprovalTask(id, tenantId, instanceId, workflowKey, stepKey, assignedRole,
                ApprovalOutcome.REJECTED, slaDueAt, createdAt, Instant.now(), requireDecider(decidedBy), comment);
    }

    /**
     * Returns an escalated copy — the SLA elapsed with no decision. The task stays open (a human
     * must still decide); escalation only raises its visibility.
     */
    public ApprovalTask escalate() {
        requireOpen();
        return new ApprovalTask(id, tenantId, instanceId, workflowKey, stepKey, assignedRole,
                ApprovalOutcome.ESCALATED, slaDueAt, createdAt, decidedAt, decidedBy, comment);
    }

    private void requireOpen() {
        if (!outcome.isOpen())
            throw new IllegalStateException("approval task " + id + " already decided: " + outcome);
    }

    private static String requireDecider(String decidedBy) {
        if (decidedBy == null || decidedBy.isBlank()) throw new IllegalArgumentException("decidedBy required");
        return decidedBy;
    }
}
