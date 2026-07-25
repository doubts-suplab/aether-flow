package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.SlaPolicy;

import java.util.Optional;

/**
 * Port interface for per-tenant SLA policy persistence.
 *
 * <p>Stores the SLA budget and escalation chain a tenant has configured for its human-approval gates.
 * Reads are scoped by {@code tenantId}; a tenant with no stored policy falls back to
 * {@link SlaPolicy#defaultFor(String)}. Implementations live in {@code flow-engine}.</p>
 */
public interface SlaPolicyStore {

    /**
     * Looks up a tenant's SLA policy.
     *
     * @param tenantId the owning tenant
     * @return the stored policy, or empty if the tenant uses defaults
     */
    Optional<SlaPolicy> find(String tenantId);

    /**
     * Persists (inserts or replaces) a tenant's SLA policy.
     *
     * @param policy the policy to store
     */
    void save(SlaPolicy policy);
}
