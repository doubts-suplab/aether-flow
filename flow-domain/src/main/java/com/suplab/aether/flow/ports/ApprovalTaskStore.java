package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.ApprovalTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for approval-task persistence — the human review queue.
 *
 * <p>Stores {@link ApprovalTask} gates raised by the orchestration engine. Every read and write is
 * scoped by {@code tenantId} so there is no cross-tenant access path. Implementations live in
 * {@code flow-engine}.</p>
 */
public interface ApprovalTaskStore {

    /**
     * Persists an approval task. Uses UPSERT semantics keyed on the task ID.
     *
     * @param task the task to persist
     */
    void save(ApprovalTask task);

    /**
     * Looks up a single task by ID within a tenant.
     *
     * @param tenantId the owning tenant
     * @param taskId   the task's ID
     * @return the task if present for this tenant, otherwise empty
     */
    Optional<ApprovalTask> findById(String tenantId, UUID taskId);

    /**
     * Lists open tasks (PENDING or ESCALATED) assigned to a role, oldest first (so the longest
     * waiting review surfaces at the top of the queue).
     *
     * @param tenantId the owning tenant
     * @param role     the assigned role
     * @param limit    maximum number of tasks to return
     * @return open tasks for the role (may be empty)
     */
    List<ApprovalTask> findOpenByRole(String tenantId, String role, int limit);

    /**
     * Finds the open task for a workflow instance, if one exists. An instance parked at a
     * human-approval gate has exactly one open task.
     *
     * @param tenantId   the owning tenant
     * @param instanceId the workflow instance
     * @return the instance's open task, or empty if none is open
     */
    Optional<ApprovalTask> findOpenByInstance(String tenantId, UUID instanceId);
}
