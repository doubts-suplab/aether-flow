package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.DeferralErasureResult;

/**
 * Erases a Grid deferral and its approval history on a right-to-erasure request (GDPR Art. 17) —
 * Flow's data-subject seam.
 *
 * <p>A deferred decision, and the {@code ApprovalTask}(s) raised to review it, may reference a data
 * subject's matter. When erasure is requested for a {@code correlationId}, this port purges the
 * corresponding {@code grid-deferral} workflow instance and every approval task raised for it,
 * scoped to the tenant. Erasure is precise (per correlation, not tenant-wide) and idempotent — a
 * correlation with nothing to erase is a no-op that returns zero counts.</p>
 *
 * <p>Unlike escalation or notification, erasure is <strong>not</strong> best-effort — it must
 * actually delete and report the counts removed, so the deletion is auditable.</p>
 */
public interface ApprovalErasurePort {

    /**
     * Erases the deferral instance and approval history for a correlation id within a tenant.
     *
     * @param tenantId      the owning tenant (isolation boundary)
     * @param correlationId the deferral's correlation id
     * @return the counts actually removed (zero when nothing matched)
     */
    DeferralErasureResult eraseDeferral(String tenantId, String correlationId);
}
