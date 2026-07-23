package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for workflow-definition persistence.
 *
 * <p>Stores the versioned {@link WorkflowDefinition} process template. Every read and write is
 * scoped by {@link FlowScope} ({@code tenantId} + {@code workflowKey}) so there is no cross-tenant
 * or cross-workflow access path. Implementations live in {@code flow-engine}.</p>
 */
public interface WorkflowDefinitionStore {

    /**
     * Persists a definition. Uses UPSERT semantics keyed on the definition ID.
     *
     * @param definition the definition to persist
     */
    void save(WorkflowDefinition definition);

    /**
     * Returns the active definition for a scope — the one new instances are started from.
     *
     * @param scope the owning tenant + workflow key
     * @return the active definition if present, otherwise empty
     */
    Optional<WorkflowDefinition> findActive(FlowScope scope);

    /**
     * Returns a specific version of a definition, active or not. The engine resolves a running
     * instance against the exact version it started under (its {@code definitionVersion}), so a
     * newly published version never changes how in-flight instances execute.
     *
     * @param scope   the owning tenant + workflow key
     * @param version the definition version to resolve
     * @return the definition at that version if present, otherwise empty
     */
    Optional<WorkflowDefinition> findByVersion(FlowScope scope, int version);

    /**
     * Lists all definitions for a tenant, most recently updated first.
     *
     * @param tenantId the owning tenant
     * @param limit    maximum number of definitions to return
     * @return definitions owned by the tenant (may be empty)
     */
    List<WorkflowDefinition> findByTenant(String tenantId, int limit);

    /**
     * Deletes a definition. Scope is required to prevent cross-workflow deletion.
     *
     * @param scope the owning tenant + workflow key
     */
    void delete(FlowScope scope);
}
