package com.suplab.aether.flow.domain;

/**
 * The ownership key for workflow orchestration: a {@code workflowKey} within a {@code tenantId}.
 *
 * <p>A {@code workflowKey} is the stable business key of a workflow definition (for example
 * {@code "invoice-approval"} or {@code "grid-deferral"}), independent of its version. Every read
 * and write into the definition, instance, and approval-task stores is scoped by this pair —
 * there is no cross-tenant or cross-workflow access path that does not pass a {@code FlowScope}.
 * This is the multi-tenancy boundary of Aether Flow, analogous to Aether Memory's per-{@code
 * (tenantId, teamId)} scoping and Aether Vault's per-{@code (tenantId, collectionId)} scoping,
 * keyed here on a <em>workflow definition</em>.</p>
 */
public record FlowScope(String tenantId, String workflowKey) {

    public FlowScope {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (workflowKey == null || workflowKey.isBlank())
            throw new IllegalArgumentException("workflowKey required");
    }

    /**
     * Convenience factory mirroring the {@code of(...)} idiom used across the domain.
     */
    public static FlowScope of(String tenantId, String workflowKey) {
        return new FlowScope(tenantId, workflowKey);
    }
}
