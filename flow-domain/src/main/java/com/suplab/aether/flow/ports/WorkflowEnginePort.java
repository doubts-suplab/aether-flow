package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowInstance;

import java.util.UUID;

/**
 * Port interface for workflow orchestration — the process state machine.
 *
 * <p>Starting an instance advances it through automated and agent steps until it either parks at a
 * {@link com.suplab.aether.flow.domain.StepType#HUMAN_APPROVAL} gate (raising an
 * {@link com.suplab.aether.flow.domain.ApprovalTask}) or reaches the terminal
 * {@link com.suplab.aether.flow.domain.StepType#END} step. A human decision on the open task
 * resumes the parked instance: an approval advances it to the next step (and onward until the next
 * park or completion), a rejection stops it. Every transition is persisted so state survives a
 * restart. Implementations live in {@code flow-engine}.</p>
 */
public interface WorkflowEnginePort {

    /**
     * Starts a new instance of the active definition for a scope and drives it to its first stable
     * state (parked at an approval gate, or completed).
     *
     * @param scope       the owning tenant + workflow key
     * @param businessKey caller correlation key (may be {@code null})
     * @return the persisted instance in its resulting state
     * @throws IllegalStateException if no active definition exists for the scope
     */
    WorkflowInstance start(FlowScope scope, String businessKey);

    /**
     * Approves the open approval task and resumes its parked instance, advancing it to the next
     * stable state.
     *
     * @param tenantId  the owning tenant
     * @param taskId    the open approval task
     * @param decidedBy who approved
     * @param comment   optional decision note
     * @return the persisted instance in its resulting state
     * @throws IllegalArgumentException if the task or its instance cannot be found
     * @throws IllegalStateException    if the task is already decided
     */
    WorkflowInstance approve(String tenantId, UUID taskId, String decidedBy, String comment);

    /**
     * Rejects the open approval task and stops its parked instance in {@code REJECTED}.
     *
     * @param tenantId  the owning tenant
     * @param taskId    the open approval task
     * @param decidedBy who rejected
     * @param comment   optional decision note
     * @return the persisted instance in {@code REJECTED}
     * @throws IllegalArgumentException if the task or its instance cannot be found
     * @throws IllegalStateException    if the task is already decided
     */
    WorkflowInstance reject(String tenantId, UUID taskId, String decidedBy, String comment);

    /**
     * Cancels a non-terminal instance (an operator action). If the instance is parked at an
     * approval gate, its open task is withdrawn — the review is moot — so it leaves the queue
     * without a human decision. The instance stops in {@code CANCELLED}.
     *
     * @param tenantId    the owning tenant
     * @param instanceId  the instance to cancel
     * @param cancelledBy who requested the cancellation
     * @param reason      optional cancellation note
     * @return the persisted instance in {@code CANCELLED}
     * @throws IllegalArgumentException if the instance cannot be found
     * @throws IllegalStateException    if the instance is already in a terminal state
     */
    WorkflowInstance cancel(String tenantId, UUID instanceId, String cancelledBy, String reason);
}
