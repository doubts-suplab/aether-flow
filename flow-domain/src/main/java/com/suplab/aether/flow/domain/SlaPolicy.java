package com.suplab.aether.flow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Per-tenant SLA governance policy for human-approval gates.
 *
 * <p>Three things a tenant configures: the default SLA budget applied when an escalated task's clock
 * is reset, the <strong>escalation chain</strong> — an ordered list of roles a breached task is routed
 * through (e.g. {@code ["reviewer-lead", "manager", "executive"]}) — and an optional
 * <strong>business-hours calendar</strong>. When an open task breaches its deadline, the escalation
 * sweep reassigns it to the next role in the chain and gives it a fresh budget; if that authority also
 * misses it, the next sweep escalates further, until the chain is exhausted (after which the task stays
 * {@code ESCALATED}, visible at the top of the queue, awaiting a human). An empty chain preserves the
 * original behaviour: a breached task is simply flagged {@code ESCALATED} without reassignment.</p>
 *
 * <p>When a {@link BusinessHours} calendar is set, SLA budgets are measured in <em>working time</em>:
 * a deadline advances only through the tenant's working window, so overnight and weekend hours do not
 * count against a reviewer. A {@code null} calendar keeps the previous 24/7 behaviour (plain wall
 * time). {@link #deadlineFrom(Instant, int)} is the single place both the initial raise and the
 * escalation reset compute a deadline, so the two stay consistent.</p>
 *
 * @param tenantId          the tenant this policy governs
 * @param defaultSlaMinutes SLA budget (minutes) granted to each escalation level (>= 0)
 * @param escalationChain   ordered roles to escalate through (may be empty; never null)
 * @param businessHours     optional working-hours calendar; {@code null} → 24/7 wall-clock SLAs
 */
public record SlaPolicy(String tenantId, int defaultSlaMinutes, List<String> escalationChain,
                        BusinessHours businessHours) {

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
     * Backward-compatible constructor for a policy with no business-hours calendar (24/7 SLAs).
     */
    public SlaPolicy(String tenantId, int defaultSlaMinutes, List<String> escalationChain) {
        this(tenantId, defaultSlaMinutes, escalationChain, null);
    }

    /**
     * The default policy for a tenant with no stored configuration: a {@value #DEFAULT_SLA_MINUTES}
     * minute budget, no escalation chain (breach → flagged {@code ESCALATED} only), and no calendar
     * (24/7 SLAs).
     */
    public static SlaPolicy defaultFor(String tenantId) {
        return new SlaPolicy(tenantId, DEFAULT_SLA_MINUTES, List.of(), null);
    }

    /**
     * @return {@code true} if this policy measures SLA budgets against a business-hours calendar.
     */
    public boolean hasBusinessHours() {
        return businessHours != null;
    }

    /**
     * Computes an SLA deadline for {@code budgetMinutes} of budget starting at {@code start}. With a
     * business-hours calendar the budget is consumed in working time only; without one it is plain
     * wall-clock minutes. Negative budgets are treated as zero.
     *
     * @param start         the instant the SLA clock starts
     * @param budgetMinutes the SLA budget in minutes
     * @return the deadline instant
     */
    public Instant deadlineFrom(Instant start, int budgetMinutes) {
        int budget = Math.max(0, budgetMinutes);
        return businessHours != null
                ? businessHours.deadlineAfter(start, budget)
                : start.plusSeconds(60L * budget);
    }

    /**
     * Returns a copy of this policy with the given business-hours calendar ({@code null} → 24/7).
     */
    public SlaPolicy withBusinessHours(BusinessHours calendar) {
        return new SlaPolicy(tenantId, defaultSlaMinutes, escalationChain, calendar);
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
