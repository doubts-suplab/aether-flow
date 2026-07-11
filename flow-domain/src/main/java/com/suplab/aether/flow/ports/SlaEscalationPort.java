package com.suplab.aether.flow.ports;

/**
 * Port interface for SLA escalation — keeping the human review queue honest.
 *
 * <p>Approval tasks carry an SLA deadline. When a {@code PENDING} task passes its deadline with no
 * decision, the review is late and needs a higher authority's attention. The escalation sweep
 * flags every breached {@code PENDING} task as {@code ESCALATED} so it surfaces at the top of the
 * queue. Escalation raises visibility — it never auto-decides; a human must still approve or
 * reject. This mirrors Aether Vault's freshness sweep: a scheduled, set-based marking pass that
 * changes state but never removes work.</p>
 */
public interface SlaEscalationPort {

    /**
     * Outcome of one escalation sweep across all workflows.
     *
     * @param scannedCount    open tasks examined this run
     * @param escalatedCount  tasks transitioned to {@code ESCALATED} this run
     * @param totalOpen       tasks still open (PENDING or ESCALATED) after the run
     */
    record EscalationResult(long scannedCount, long escalatedCount, long totalOpen) {}

    /**
     * Runs one escalation sweep: marks breached {@code PENDING} tasks as {@code ESCALATED}.
     *
     * @return the sweep result
     */
    EscalationResult sweep();
}
