package com.suplab.aether.flow.domain;

/**
 * The execution state of a {@link WorkflowInstance}.
 *
 * <ul>
 *   <li>RUNNING — the engine is advancing the instance through automated/agent steps</li>
 *   <li>WAITING_APPROVAL — parked at a {@link StepType#HUMAN_APPROVAL} step, awaiting a human
 *       decision via an {@link ApprovalTask}</li>
 *   <li>COMPLETED — reached the {@link StepType#END} step successfully</li>
 *   <li>REJECTED — a human rejected an approval gate; the instance stops without completing</li>
 *   <li>CANCELLED — an operator cancelled the instance before it finished</li>
 *   <li>FAILED — the engine could not advance the instance (for example a broken transition)</li>
 * </ul>
 */
public enum WorkflowStatus {
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    REJECTED,
    CANCELLED,
    FAILED;

    /**
     * @return {@code true} if no further transitions are possible from this state.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED || this == FAILED;
    }

    /**
     * @return {@code true} if the instance is parked awaiting a human approval decision.
     */
    public boolean isWaiting() {
        return this == WAITING_APPROVAL;
    }
}
