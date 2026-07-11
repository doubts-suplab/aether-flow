package com.suplab.aether.flow.domain;

import java.time.Instant;

/**
 * A DEFER decision handed off from Aether Grid into Aether Flow for human review.
 *
 * <p>Grid enforces a confidence gate: an agent decision below the confidence threshold is never
 * auto-actioned — it is <em>deferred</em> to a human. This record is the bounded projection Grid
 * sends across the boundary: a correlation id, the tenant, a coarse agent identifier, a
 * human-readable {@code summary} of what needs deciding, and the {@code confidence} that triggered
 * the deferral. It deliberately carries no raw request payloads, no PII, and no Grid-internal
 * identifiers beyond the correlation id — Flow orchestrates the review without importing Grid's
 * internals, mirroring the privacy-preserving projection Aether Memory uses at its federation
 * boundary.</p>
 *
 * <p>Flow converts a {@code DeferredDecision} into a workflow instance parked at a human-approval
 * gate; the approver's decision is the answer Grid awaits.</p>
 *
 * @param correlationId Grid's decision correlation id — the key to report the outcome back
 * @param tenantId      owning tenant (isolation boundary)
 * @param agentId       coarse identifier of the deferring agent (no request internals)
 * @param summary       bounded, human-readable description of what needs deciding
 * @param confidence    the agent confidence that triggered the deferral (0.0–1.0)
 * @param requestedRole the role that should review this deferral
 * @param receivedAt    when Flow received the deferral
 */
public record DeferredDecision(
        String correlationId,
        String tenantId,
        String agentId,
        String summary,
        double confidence,
        String requestedRole,
        Instant receivedAt
) {
    /** Grid's confidence gate: decisions at or above this confidence are not deferred. */
    public static final double CONFIDENCE_GATE = 0.8;

    public DeferredDecision {
        if (correlationId == null || correlationId.isBlank())
            throw new IllegalArgumentException("correlationId required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId required");
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary required");
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        if (requestedRole == null || requestedRole.isBlank()) requestedRole = "reviewer";
        if (receivedAt == null) receivedAt = Instant.now();
    }

    /**
     * @return {@code true} if this decision is below Grid's confidence gate — i.e. genuinely
     *         warranted a human deferral rather than auto-action.
     */
    public boolean isBelowGate() {
        return confidence < CONFIDENCE_GATE;
    }
}
