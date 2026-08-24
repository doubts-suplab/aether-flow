package com.suplab.aether.flow.domain;

import java.time.Instant;

/**
 * The human decision on a deferred Grid decision — the answer Aether Grid awaits.
 *
 * <p>When Grid's confidence gate defers a decision (a {@link DeferredDecision}), Flow parks it at a
 * human-approval gate. Once a person approves or rejects, this bounded projection reports the outcome
 * back to Grid, keyed by the original {@code correlationId} so Grid can resume the correlated work. It
 * carries no request internals and no PII beyond the decider's identifier — the same
 * privacy-preserving discipline the inbound {@link DeferredDecision} follows.</p>
 *
 * @param correlationId the deferral's correlation id (matches the {@link DeferredDecision})
 * @param tenantId      owning tenant (isolation boundary)
 * @param decision      the terminal human decision — {@code "APPROVED"} or {@code "REJECTED"}
 * @param decidedBy     who decided
 * @param decidedAt     when the decision was made
 */
public record DeferralOutcome(
        String correlationId,
        String tenantId,
        String decision,
        String decidedBy,
        Instant decidedAt
) {
    /** The two terminal decisions Flow reports back to Grid. */
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    public DeferralOutcome {
        if (correlationId == null || correlationId.isBlank())
            throw new IllegalArgumentException("correlationId required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (!APPROVED.equals(decision) && !REJECTED.equals(decision))
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        if (decidedAt == null) decidedAt = Instant.now();
    }

    /** An approval outcome for a deferral. */
    public static DeferralOutcome approved(String correlationId, String tenantId, String decidedBy) {
        return new DeferralOutcome(correlationId, tenantId, APPROVED, decidedBy, Instant.now());
    }

    /** A rejection outcome for a deferral. */
    public static DeferralOutcome rejected(String correlationId, String tenantId, String decidedBy) {
        return new DeferralOutcome(correlationId, tenantId, REJECTED, decidedBy, Instant.now());
    }
}
