package com.suplab.aether.flow.engine.erasure;

import com.suplab.aether.flow.domain.DeferralErasureResult;
import com.suplab.aether.flow.domain.DeferredDecision;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.ports.ApprovalErasurePort;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default {@link ApprovalErasurePort} — right-to-erasure for a Grid deferral and its approval
 * history (GDPR Art. 17).
 *
 * <p>Erasure is keyed by the deferral's {@code correlationId} within the tenant's canonical
 * {@code grid-deferral} scope: it resolves the deferral instance, deletes every approval task raised
 * for it (the child rows first, so no orphaned review history remains), then deletes the instance
 * itself. It reports the counts actually removed. A correlation with nothing to erase is a no-op that
 * returns zero counts — the operation is idempotent and safe to retry.</p>
 *
 * <p>The service is framework-free and depends only on port interfaces; the API module assembles it
 * via constructor injection.</p>
 */
public class DefaultApprovalErasureService implements ApprovalErasurePort {

    private static final Logger log = LoggerFactory.getLogger(DefaultApprovalErasureService.class);

    private final WorkflowInstanceStore instanceStore;
    private final ApprovalTaskStore approvalTaskStore;

    public DefaultApprovalErasureService(WorkflowInstanceStore instanceStore,
                                         ApprovalTaskStore approvalTaskStore) {
        this.instanceStore = instanceStore;
        this.approvalTaskStore = approvalTaskStore;
    }

    @Override
    public DeferralErasureResult eraseDeferral(String tenantId, String correlationId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (correlationId == null || correlationId.isBlank())
            throw new IllegalArgumentException("correlationId required");

        var scope = FlowScope.of(tenantId, DeferredDecision.WORKFLOW_KEY);

        // Delete approval tasks for the resolved deferral instance first (child rows), then the
        // instance itself — leaving no orphaned review history behind.
        int tasksErased = instanceStore.findByBusinessKey(scope, correlationId)
                .map(instance -> approvalTaskStore.deleteByInstance(tenantId, instance.id()))
                .orElse(0);
        int instancesErased = instanceStore.deleteByBusinessKey(scope, correlationId);

        var result = new DeferralErasureResult(correlationId, tenantId, instancesErased, tasksErased);
        if (result.isEmpty()) {
            log.info("Erasure requested for correlationId={} tenantId={} — nothing to erase", correlationId, tenantId);
        } else {
            log.info("Erased deferral correlationId={} tenantId={} instances={} tasks={}",
                    correlationId, tenantId, instancesErased, tasksErased);
        }
        return result;
    }
}
