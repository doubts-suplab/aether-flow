package com.suplab.aether.flow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A running (or finished) execution of a {@link WorkflowDefinition}.
 *
 * <p>An instance tracks <em>where</em> in the process it is ({@code currentStepKey}) and
 * <em>what state</em> it is in ({@link WorkflowStatus}). It is pinned to the definition version it
 * started under ({@code definitionVersion}) so a running instance is unaffected when the
 * definition is later revised. A {@code businessKey} correlates the instance to the caller's
 * domain object (an invoice number, a Grid decision id, …).</p>
 *
 * <p>All fields are immutable; state transitions ({@link #moveTo(WorkflowStep)},
 * {@link #park(WorkflowStep)}, {@link #complete()}, {@link #reject()}, {@link #cancel()},
 * {@link #fail()}) return new instances. Persistence of state across restarts is provided by the
 * instance store — the state machine itself is pure.</p>
 *
 * @param id                stable identifier
 * @param tenantId          owning tenant (isolation boundary)
 * @param workflowKey       business key of the definition this runs
 * @param definitionVersion the definition version this instance is pinned to
 * @param businessKey       caller-supplied correlation key (may be {@code null})
 * @param currentStepKey    the step the instance is currently at
 * @param status            execution state
 * @param startedAt         when the instance was started
 * @param updatedAt         when the instance last transitioned
 * @param completedAt       when the instance reached a terminal state ({@code null} until then)
 */
public record WorkflowInstance(
        UUID id,
        String tenantId,
        String workflowKey,
        int definitionVersion,
        String businessKey,
        String currentStepKey,
        WorkflowStatus status,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt
) {
    public WorkflowInstance {
        if (id == null) id = UUID.randomUUID();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (workflowKey == null || workflowKey.isBlank())
            throw new IllegalArgumentException("workflowKey required");
        if (definitionVersion < 1) throw new IllegalArgumentException("definitionVersion must be >= 1");
        if (currentStepKey == null || currentStepKey.isBlank())
            throw new IllegalArgumentException("currentStepKey required");
        if (status == null) status = WorkflowStatus.RUNNING;
        if (startedAt == null) startedAt = Instant.now();
        if (updatedAt == null) updatedAt = startedAt;
    }

    /**
     * Factory for a fresh instance positioned at a definition's start step, status {@code RUNNING}.
     *
     * @param definition the definition to instantiate
     * @param businessKey caller correlation key (may be {@code null})
     */
    public static WorkflowInstance start(WorkflowDefinition definition, String businessKey) {
        var now = Instant.now();
        return new WorkflowInstance(UUID.randomUUID(), definition.tenantId(), definition.workflowKey(),
                definition.version(), businessKey, definition.startStep().key(), WorkflowStatus.RUNNING,
                now, now, null);
    }

    /**
     * Returns the owning scope ({@code tenantId} + {@code workflowKey}) of this instance.
     */
    public FlowScope scope() {
        return new FlowScope(tenantId, workflowKey);
    }

    /**
     * Returns a copy advanced to the given step, status {@code RUNNING}. Rejects transitions from a
     * terminal state.
     *
     * @param step the step to move to
     */
    public WorkflowInstance moveTo(WorkflowStep step) {
        requireNotTerminal();
        return new WorkflowInstance(id, tenantId, workflowKey, definitionVersion, businessKey,
                step.key(), WorkflowStatus.RUNNING, startedAt, Instant.now(), null);
    }

    /**
     * Returns a copy parked at a human-approval step, status {@code WAITING_APPROVAL}.
     *
     * @param step the approval step to park at
     */
    public WorkflowInstance park(WorkflowStep step) {
        requireNotTerminal();
        return new WorkflowInstance(id, tenantId, workflowKey, definitionVersion, businessKey,
                step.key(), WorkflowStatus.WAITING_APPROVAL, startedAt, Instant.now(), null);
    }

    /**
     * Returns a copy in terminal state {@code COMPLETED}, {@code completedAt} set to now.
     */
    public WorkflowInstance complete() {
        requireNotTerminal();
        var now = Instant.now();
        return new WorkflowInstance(id, tenantId, workflowKey, definitionVersion, businessKey,
                currentStepKey, WorkflowStatus.COMPLETED, startedAt, now, now);
    }

    /**
     * Returns a copy in terminal state {@code REJECTED} (a human rejected an approval gate).
     */
    public WorkflowInstance reject() {
        requireNotTerminal();
        var now = Instant.now();
        return new WorkflowInstance(id, tenantId, workflowKey, definitionVersion, businessKey,
                currentStepKey, WorkflowStatus.REJECTED, startedAt, now, now);
    }

    /**
     * Returns a copy in terminal state {@code CANCELLED} (an operator cancelled the instance).
     */
    public WorkflowInstance cancel() {
        requireNotTerminal();
        var now = Instant.now();
        return new WorkflowInstance(id, tenantId, workflowKey, definitionVersion, businessKey,
                currentStepKey, WorkflowStatus.CANCELLED, startedAt, now, now);
    }

    /**
     * Returns a copy in terminal state {@code FAILED} (the engine could not advance the instance).
     */
    public WorkflowInstance fail() {
        requireNotTerminal();
        var now = Instant.now();
        return new WorkflowInstance(id, tenantId, workflowKey, definitionVersion, businessKey,
                currentStepKey, WorkflowStatus.FAILED, startedAt, now, now);
    }

    private void requireNotTerminal() {
        if (status.isTerminal())
            throw new IllegalStateException("instance " + id + " is in terminal state " + status);
    }
}
