package com.suplab.aether.flow.domain;

/**
 * The state of an {@link ApprovalTask} — a human review gate.
 *
 * <ul>
 *   <li>PENDING — raised and awaiting a human decision</li>
 *   <li>APPROVED — a human approved; the workflow instance advances past the gate</li>
 *   <li>REJECTED — a human rejected; the workflow instance stops in {@code REJECTED}</li>
 *   <li>ESCALATED — the SLA elapsed with no decision; the task is flagged for a higher authority
 *       but remains actionable (escalation raises visibility, it does not auto-decide)</li>
 * </ul>
 */
public enum ApprovalOutcome {
    PENDING,
    APPROVED,
    REJECTED,
    ESCALATED;

    /**
     * @return {@code true} if the task still awaits a human decision (PENDING or ESCALATED).
     */
    public boolean isOpen() {
        return this == PENDING || this == ESCALATED;
    }

    /**
     * @return {@code true} if a human has decided the task (APPROVED or REJECTED).
     */
    public boolean isDecided() {
        return this == APPROVED || this == REJECTED;
    }
}
