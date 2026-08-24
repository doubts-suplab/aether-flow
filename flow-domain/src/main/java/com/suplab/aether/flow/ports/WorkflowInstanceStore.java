package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for workflow-instance persistence — the state that survives service restarts.
 *
 * <p>Persisting every state transition is what makes Aether Flow's <em>state persistence</em>
 * capability real: a restarted service resumes running instances from their last saved step.
 * Every read and write is scoped by {@code tenantId} (and {@code workflowKey} where applicable) so
 * there is no cross-tenant access path. Implementations live in {@code flow-engine}.</p>
 */
public interface WorkflowInstanceStore {

    /**
     * Persists an instance. Uses UPSERT semantics keyed on the instance ID.
     *
     * @param instance the instance to persist
     */
    void save(WorkflowInstance instance);

    /**
     * Looks up a single instance by ID within a scope.
     *
     * @param scope      the owning tenant + workflow key
     * @param instanceId the instance's ID
     * @return the instance if present in this scope, otherwise empty
     */
    Optional<WorkflowInstance> findById(FlowScope scope, UUID instanceId);

    /**
     * Looks up an instance by ID within a tenant, regardless of workflow key. Used by the approval
     * path, which resolves the instance from an approval task.
     *
     * @param tenantId   the owning tenant
     * @param instanceId the instance's ID
     * @return the instance if present for this tenant, otherwise empty
     */
    Optional<WorkflowInstance> findByTenantAndId(String tenantId, UUID instanceId);

    /**
     * Lists instances of a workflow in a given status, most recently updated first.
     *
     * @param scope  the owning tenant + workflow key
     * @param status the status to filter by
     * @param limit  maximum number of instances to return
     * @return matching instances (may be empty)
     */
    List<WorkflowInstance> findByStatus(FlowScope scope, WorkflowStatus status, int limit);

    /**
     * Counts instances of a workflow in a given status.
     *
     * @param scope  the owning tenant + workflow key
     * @param status the status to count
     * @return non-negative instance count
     */
    long countByStatus(FlowScope scope, WorkflowStatus status);

    /**
     * Looks up the most recently created instance carrying a given {@code businessKey} within a scope
     * — the correlation key an upstream caller supplied (e.g. a Grid deferral's correlation id). Used
     * to make deferral intake idempotent: a re-delivered correlation id resolves to the existing
     * instance rather than starting a duplicate.
     *
     * @param scope       the owning tenant + workflow key
     * @param businessKey the correlation key to match (never blank)
     * @return the matching instance if present, otherwise empty
     */
    Optional<WorkflowInstance> findByBusinessKey(FlowScope scope, String businessKey);
}
