package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.ApprovalTask;

/**
 * Port for notifying reviewers about approval-task lifecycle events.
 *
 * <p>Flow raises a signal when a task is created (a review is waiting) and when a task is escalated
 * (a review is overdue and has moved up the chain). The default implementation logs; webhook and
 * email sinks are adapters behind this same port — Flow's core never talks to a transport directly,
 * mirroring how the LLM and embedding seams are pluggable elsewhere in the ecosystem. Notification is
 * best-effort: a failing sink must never break task raising or the escalation sweep.</p>
 */
public interface ApprovalNotificationPort {

    /**
     * Signals that a new approval task has been raised and is awaiting a decision.
     *
     * @param task the freshly raised task
     */
    void notifyRaised(ApprovalTask task);

    /**
     * Signals that an open task breached its SLA and was escalated (possibly reassigned up the chain).
     *
     * @param task the escalated task
     */
    void notifyEscalated(ApprovalTask task);
}
