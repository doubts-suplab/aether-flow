package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.ApprovalTask;

/**
 * Port for recording operator-facing counters over the approval-task lifecycle.
 *
 * <p>The engine and the review queue call this at each human-driven transition — a task is raised,
 * approved, rejected, or reassigned — so an operator dashboard can watch queue throughput without the
 * core ever depending on a metrics library. A Micrometer adapter lives in the API module; the engine
 * stays framework-free, mirroring the {@link ApprovalNotificationPort} seam.</p>
 *
 * <p>Escalation and the open-queue depth are metered separately by the escalation sweep (which already
 * emits {@code aether.flow.escalation.escalated} and the {@code aether.flow.approvals.open} gauge), so
 * this port deliberately does not duplicate them. Recording is side-effect-free beyond the counter and
 * must never influence a decision.</p>
 */
public interface ApprovalMetricsPort {

    /** Records that a new approval task was raised (parked at a human gate or from a Grid deferral). */
    void recordRaised(ApprovalTask task);

    /** Records that a task was approved by a human. */
    void recordApproved(ApprovalTask task);

    /** Records that a task was rejected by a human. */
    void recordRejected(ApprovalTask task);

    /** Records that an open task was reassigned (delegated) to another role. */
    void recordReassigned(ApprovalTask task);

    /**
     * A no-op implementation so the engine and controller run without a metrics backend (unit tests,
     * standalone). The API module substitutes a Micrometer-backed adapter.
     */
    ApprovalMetricsPort NO_OP = new ApprovalMetricsPort() {
        @Override public void recordRaised(ApprovalTask task) { }
        @Override public void recordApproved(ApprovalTask task) { }
        @Override public void recordRejected(ApprovalTask task) { }
        @Override public void recordReassigned(ApprovalTask task) { }
    };
}
