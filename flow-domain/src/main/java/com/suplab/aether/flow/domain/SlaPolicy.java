package com.suplab.aether.flow.domain;

import java.util.List;
import java.util.Optional;

/**
 * Per-tenant SLA governance policy for human-approval gates.
 *
 * <p>Two things a tenant configures: the default SLA budget applied when an escalated task's clock is
 * reset, and the <strong>escalation chain</strong> — an ordered list of roles a breached task is
 * routed through (e.g. {@code ["reviewer-lead", "manager", "executive"]}). When an open task breaches
 * its deadline, the escalation sweep reassigns it to the next role in the chain and gives it a fresh
 * budget; if that authority also misses it, the next sweep escalates further, until the chain is
 * exhausted (after which the task stays {@code ESCALATED}, visible at the top of the queue, awaiting a
 * human). An empty chain preserves the original behaviour: a breached task is simply flagged
 * {@code ESCALATED} without reassignment.</p>
 *
 * <p>Business-hours calendars are a later-phase refinement; this policy expresses budgets and the
 * routing ladder only.</p>
 *
 * @param tenantId          the tenant this policy governs
 * @param defaultSlaMinutes SLA budget (minutes) granted to each escalation level (>= 0)
 * @param escalationChain   ordered roles to escalate through (may be empty; never null)
 */
public record SlaPolicy(String tenantId, int defaultSlaMinutes, List<String> escalationChain) {

    /** Default SLA budget when a tenant has no stored policy. */
    public static final int DEFAULT_SLA_MINUTES = 60;

    public SlaPolicy {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (defaultSlaMinutes < 0) throw new IllegalArgumentException("defaultSlaMinutes must be >= 0");
        if (escalationChain == null) {
            escalationChain = List.of();
        } else {
            var cleaned = escalationChain.stream()
                    .filter(r -> r != null && !r.isBlank())
                    .map(String::trim)
                    .toList();
            escalationChain = List.copyOf(cleaned);
        }
    }

    /**
     * The default policy for a tenant with no stored configuration: a {@value #DEFAULT_SLA_MINUTES}
     * minute budget and no escalation chain (breach → flagged {@code ESCALATED} only).
     */
    public static SlaPolicy defaultFor(String tenantId) {
        return new SlaPolicy(tenantId, DEFAULT_SLA_MINUTES, List.of());
    }

    /**
     * @param level the current escalation level (0 = never escalated)
     * @return {@code true} if the chain has a role to escalate to at this level.
     */
    public boolean hasNextLevel(int level) {
        return level >= 0 && level < escalationChain.size();
    }

    /**
     * The role to escalate to at a given level, if the chain reaches that far.
     *
     * @param level the current escalation level
     * @return the next role, or empty if the chain is exhausted
     */
    public Optional<String> roleAtLevel(int level) {
        return hasNextLevel(level) ? Optional.of(escalationChain.get(level)) : Optional.empty();
    }
}
