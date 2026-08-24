package com.suplab.aether.flow.domain;

/**
 * The outcome of erasing a Grid deferral and its approval history — Flow's contribution to the
 * ecosystem's right-to-erasure (GDPR Art. 17).
 *
 * <p>Erasure is keyed by the deferral's {@code correlationId} (a specific deferred decision), scoped
 * to a tenant. The counts report what was actually removed so a caller (or an upstream data-subject
 * request in Grid) can audit the deletion. A {@code correlationId} with nothing to erase returns
 * zero counts — the operation is idempotent.</p>
 *
 * @param correlationId    the erased deferral's correlation id
 * @param tenantId         owning tenant (isolation boundary)
 * @param instancesErased  number of workflow instances removed (0 or 1 under idempotent intake)
 * @param tasksErased      number of approval tasks removed for those instances
 */
public record DeferralErasureResult(
        String correlationId,
        String tenantId,
        int instancesErased,
        int tasksErased
) {
    public DeferralErasureResult {
        if (correlationId == null || correlationId.isBlank())
            throw new IllegalArgumentException("correlationId required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (instancesErased < 0 || tasksErased < 0)
            throw new IllegalArgumentException("erasure counts must be >= 0");
    }

    /** @return {@code true} if nothing matched the correlation id — nothing was erased. */
    public boolean isEmpty() {
        return instancesErased == 0 && tasksErased == 0;
    }
}
